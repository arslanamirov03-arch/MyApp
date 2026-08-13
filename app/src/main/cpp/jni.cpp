#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <string>
#include <thread>
#include <vector>
#include "whisper.h"

#define TAG "HoerpraxisJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

static std::atomic_bool g_abort{false};

static void append_escaped(std::string &out, const char *text) {
    for (const unsigned char *p = (const unsigned char *) text; *p; ++p) {
        switch (*p) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (*p < 0x20) {
                    char buf[8];
                    snprintf(buf, sizeof(buf), "\\u%04x", *p);
                    out += buf;
                } else {
                    out += (char) *p;
                }
        }
    }
}

static whisper_alignment_heads_preset aheads_for(const char *model_id) {
    if (strcmp(model_id, "medium") == 0) return WHISPER_AHEADS_MEDIUM;
    if (strcmp(model_id, "large-v3-turbo") == 0) return WHISPER_AHEADS_LARGE_V3_TURBO;
    return WHISPER_AHEADS_SMALL;
}

extern "C" JNIEXPORT jlong JNICALL
Java_app_hoerpraxis_whisper_WhisperBridge_nativeInit(
        JNIEnv *env, jobject, jstring modelPath, jstring modelId) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    const char *id = env->GetStringUTFChars(modelId, nullptr);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    // Dynamic-time-warping alignment gives per-word timestamps that follow the
    // speaker closely instead of drifting over a long recording.
    cparams.dtw_token_timestamps = true;
    cparams.dtw_aheads_preset = aheads_for(id);

    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    if (!ctx) {
        // Fall back to plain token timestamps if alignment heads are unavailable.
        cparams.dtw_token_timestamps = false;
        cparams.dtw_aheads_preset = WHISPER_AHEADS_NONE;
        ctx = whisper_init_from_file_with_params(path, cparams);
    }

    env->ReleaseStringUTFChars(modelPath, path);
    env->ReleaseStringUTFChars(modelId, id);
    LOGI("model init: %p", ctx);
    return (jlong) ctx;
}

extern "C" JNIEXPORT void JNICALL
Java_app_hoerpraxis_whisper_WhisperBridge_nativeFree(JNIEnv *, jobject, jlong ctxPtr) {
    if (ctxPtr) whisper_free((whisper_context *) ctxPtr);
}

extern "C" JNIEXPORT void JNICALL
Java_app_hoerpraxis_whisper_WhisperBridge_nativeCancel(JNIEnv *, jobject) {
    g_abort = true;
}

struct progress_ctx {
    JNIEnv *env;
    jobject bridge;
    jmethodID onProgress;
};

extern "C" JNIEXPORT jstring JNICALL
Java_app_hoerpraxis_whisper_WhisperBridge_nativeTranscribe(
        JNIEnv *env, jobject bridge, jlong ctxPtr, jfloatArray pcm) {
    auto *ctx = (whisper_context *) ctxPtr;
    if (!ctx) return env->NewStringUTF("[]");

    g_abort = false;

    jsize n = env->GetArrayLength(pcm);
    std::vector<float> samples(n);
    env->GetFloatArrayRegion(pcm, 0, n, samples.data());

    jclass cls = env->GetObjectClass(bridge);
    progress_ctx pctx{env, bridge, env->GetMethodID(cls, "onNativeProgress", "(I)V")};

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "de";
    params.translate = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.token_timestamps = true;
    params.split_on_word = true;
    params.max_len = 1;
    params.no_context = true;
    params.suppress_blank = true;
    int hw = (int) std::thread::hardware_concurrency();
    params.n_threads = hw > 1 ? (hw > 8 ? 8 : hw) : 4;

    params.progress_callback = [](whisper_context *, whisper_state *, int progress, void *ud) {
        auto *p = (progress_ctx *) ud;
        p->env->CallVoidMethod(p->bridge, p->onProgress, (jint) progress);
    };
    params.progress_callback_user_data = &pctx;

    params.abort_callback = [](void *) { return g_abort.load(); };
    params.abort_callback_user_data = nullptr;

    int rc = whisper_full(ctx, params, samples.data(), n);
    if (rc != 0 || g_abort) {
        LOGI("transcribe finished rc=%d abort=%d", rc, g_abort.load());
        return env->NewStringUTF(g_abort ? "CANCELLED" : "[]");
    }

    // With max_len=1 + split_on_word each segment is a single word. Prefer the
    // DTW timestamp of its first real token — it tracks the speaker far more
    // closely than the decoder's own segment boundaries.
    struct word_entry { std::string text; int64_t start; int64_t end; };
    std::vector<word_entry> words;

    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (!text) continue;
        while (*text == ' ') ++text;
        if (*text == '\0') continue;

        int64_t t0 = whisper_full_get_segment_t0(ctx, i);
        int64_t t1 = whisper_full_get_segment_t1(ctx, i);

        int64_t dtw = -1;
        int n_tokens = whisper_full_n_tokens(ctx, i);
        for (int j = 0; j < n_tokens; ++j) {
            if (whisper_full_get_token_id(ctx, i, j) >= whisper_token_eot(ctx)) continue;
            int64_t candidate = whisper_full_get_token_data(ctx, i, j).t_dtw;
            if (candidate >= 0) { dtw = candidate; break; }
        }

        int64_t start = dtw >= 0 ? dtw : t0;
        if (start > t1) start = t0;
        words.push_back({text, start * 10, t1 * 10});
    }

    // A word ends where the next one starts, so the highlight never lingers.
    for (size_t i = 0; i + 1 < words.size(); ++i) {
        if (words[i + 1].start > words[i].start && words[i + 1].start < words[i].end) {
            words[i].end = words[i + 1].start;
        }
    }

    std::string json = "[";
    for (size_t i = 0; i < words.size(); ++i) {
        if (i) json += ",";
        json += "{\"w\":\"";
        append_escaped(json, words[i].text.c_str());
        json += "\",\"s\":" + std::to_string(words[i].start) +
                ",\"e\":" + std::to_string(words[i].end) + "}";
    }
    json += "]";
    return env->NewStringUTF(json.c_str());
}

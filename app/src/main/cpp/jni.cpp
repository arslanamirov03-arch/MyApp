#include <jni.h>
#include <android/log.h>
#include <atomic>
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

extern "C" JNIEXPORT jlong JNICALL
Java_app_hoerpraxis_whisper_WhisperBridge_nativeInit(JNIEnv *env, jobject, jstring modelPath) {
    const char *path = env->GetStringUTFChars(modelPath, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);
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

    // With max_len=1 + split_on_word each segment is a single word carrying
    // its own timestamps (centiseconds), which we convert to milliseconds.
    std::string json = "[";
    int n_segments = whisper_full_n_segments(ctx);
    bool first = true;
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (!text) continue;
        // trim leading spaces
        while (*text == ' ') ++text;
        if (*text == '\0') continue;
        int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10;
        int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        if (!first) json += ",";
        first = false;
        json += "{\"w\":\"";
        append_escaped(json, text);
        json += "\",\"s\":" + std::to_string(t0) + ",\"e\":" + std::to_string(t1) + "}";
    }
    json += "]";
    return env->NewStringUTF(json.c_str());
}

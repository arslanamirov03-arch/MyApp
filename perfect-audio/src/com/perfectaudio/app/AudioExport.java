package com.perfectaudio.app;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

/**
 * Cuts a time range out of an audio or video file.
 * AAC sources are copied sample-for-sample into an .m4a (no re-encode, no
 * quality loss); anything else is decoded to PCM and written as a .wav.
 */
class AudioExport {

    static File extract(String srcPath, long startMs, long endMs, File outDir, String baseName)
            throws IOException {
        return edit(srcPath, startMs, endMs, true, outDir, baseName);
    }

    /**
     * Same as extract, but the caller picks the container: "m4a" (AAC, small and
     * accepted almost everywhere) or "wav" (uncompressed).
     */
    static File extractAs(String srcPath, long startMs, long endMs, File outDir,
                          String baseName, String format) throws IOException {
        File wav = null;
        try {
            if ("m4a".equals(format)) {
                File direct = edit(srcPath, startMs, endMs, true, outDir, baseName);
                if (direct.getName().endsWith(".m4a")) return direct;   // copied without re-encoding
                wav = direct;
                File out = new File(outDir, stripExt(direct.getName()) + ".m4a");
                encodeWavToM4a(wav, out);
                return out;
            }
            return edit(srcPath, startMs, endMs, true, outDir, baseName);
        } finally {
            if (wav != null && wav.exists()) wav.delete();
        }
    }

    private static String stripExt(String n) {
        int i = n.lastIndexOf('.');
        return i > 0 ? n.substring(0, i) : n;
    }

    /** Encodes a 16-bit PCM WAV into AAC inside an MP4 container. */
    private static void encodeWavToM4a(File wav, File out) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(wav, "r");
        MediaCodec enc = null;
        MediaMuxer muxer = null;
        try {
            byte[] head = new byte[44];
            raf.readFully(head);
            int channels = le16(head, 22);
            int sampleRate = le32(head, 24);
            if (channels < 1 || channels > 2) channels = 2;
            if (sampleRate < 8000) sampleRate = 44100;

            MediaFormat fmt = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, channels > 1 ? 128000 : 96000);
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32 * 1024);

            enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            enc.start();

            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track = -1;
            boolean muxing = false;

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            byte[] chunk = new byte[8192];
            long totalRead = 0;
            boolean inputDone = false, outputDone = false;
            final int bytesPerSec = sampleRate * channels * 2;

            while (!outputDone) {
                if (!inputDone) {
                    int inIdx = enc.dequeueInputBuffer(10000);
                    if (inIdx >= 0) {
                        ByteBuffer in = enc.getInputBuffer(inIdx);
                        int n = in == null ? -1 : raf.read(chunk, 0, Math.min(chunk.length, in.capacity()));
                        long ptsUs = bytesPerSec > 0 ? totalRead * 1000000L / bytesPerSec : 0;
                        if (n <= 0) {
                            enc.queueInputBuffer(inIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            in.clear();
                            in.put(chunk, 0, n);
                            enc.queueInputBuffer(inIdx, 0, n, ptsUs, 0);
                            totalRead += n;
                        }
                    }
                }
                int outIdx = enc.dequeueOutputBuffer(info, 10000);
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(enc.getOutputFormat());
                    muxer.start();
                    muxing = true;
                } else if (outIdx >= 0) {
                    ByteBuffer outBuf = enc.getOutputBuffer(outIdx);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && muxing && outBuf != null) {
                        outBuf.position(info.offset);
                        outBuf.limit(info.offset + info.size);
                        muxer.writeSampleData(track, outBuf, info);
                    }
                    enc.releaseOutputBuffer(outIdx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            if (muxing) muxer.stop();
        } catch (Exception e) {
            if (out.exists()) out.delete();
            throw new IOException("encode failed: " + e.getMessage());
        } finally {
            try { raf.close(); } catch (Exception ignored) {}
            if (enc != null) { try { enc.stop(); enc.release(); } catch (Exception ignored) {} }
            if (muxer != null) { try { muxer.release(); } catch (Exception ignored) {} }
        }
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    /**
     * keep = true  → only [startMs, endMs] survives.
     * keep = false → that range is removed and the two remaining parts are joined.
     */
    static File edit(String srcPath, long startMs, long endMs, boolean keep,
                     File outDir, String baseName) throws IOException {
        outDir.mkdirs();
        String safe = baseName.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        if (safe.isEmpty()) safe = "audio";
        if (safe.length() > 40) safe = safe.substring(0, 40);

        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(srcPath);
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0) throw new IOException("no audio track");
            extractor.selectTrack(track);

            String mime = format.getString(MediaFormat.KEY_MIME);
            if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime)) {
                File out = new File(outDir, safe + ".m4a");
                if (copyAac(extractor, format, startMs, endMs, keep, out)) return out;
                // fall through to WAV if the muxer refused the format
                extractor.release();
                extractor = new MediaExtractor();
                extractor.setDataSource(srcPath);
                extractor.selectTrack(track);
                format = extractor.getTrackFormat(track);
            }
            File out = new File(outDir, safe + ".wav");
            decodeToWav(extractor, format, startMs, endMs, keep, out);
            return out;
        } finally {
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Sample-accurate copy of compressed AAC frames into an MP4 container.
     * AAC frames are independent, so dropping a range and shifting the
     * timestamps of what follows joins the two parts without re-encoding.
     */
    private static boolean copyAac(MediaExtractor extractor, MediaFormat format,
                                   long startMs, long endMs, boolean keep, File out) {
        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(format);
            muxer.start();

            long startUs = startMs * 1000L;
            long endUs = endMs * 1000L;
            extractor.seekTo(keep ? startUs : 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            ByteBuffer buf = ByteBuffer.allocate(512 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            long firstUs = -1;
            long removedUs = endUs - startUs;
            boolean wrote = false;

            while (true) {
                int size = extractor.readSampleData(buf, 0);
                if (size < 0) break;
                long ptsUs = extractor.getSampleTime();
                long outPts;
                if (keep) {
                    if (ptsUs > endUs) break;
                    if (firstUs < 0) firstUs = ptsUs;
                    outPts = ptsUs - firstUs;
                } else {
                    if (ptsUs >= startUs && ptsUs <= endUs) {   // dropped range
                        extractor.advance();
                        continue;
                    }
                    outPts = ptsUs < startUs ? ptsUs : ptsUs - removedUs;
                }
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = outPts;
                info.flags = extractor.getSampleFlags();
                muxer.writeSampleData(outTrack, buf, info);
                wrote = true;
                extractor.advance();
            }
            muxer.stop();
            return wrote;
        } catch (Exception e) {
            out.delete();
            return false;
        } finally {
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** Decodes to 16-bit PCM and writes a WAV file: either only the range, or everything but it. */
    private static void decodeToWav(MediaExtractor extractor, MediaFormat format,
                                    long startMs, long endMs, boolean keep, File out)
            throws IOException {
        String mime = format.getString(MediaFormat.KEY_MIME);
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        int sampleRate = format.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? format.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channels = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

        long startUs = startMs * 1000L;
        long endUs = endMs * 1000L;
        extractor.seekTo(keep ? startUs : 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        OutputStream os = new FileOutputStream(out);
        writeWavHeader(os, sampleRate, channels, 0);
        long pcmBytes = 0;

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputDone = false, outputDone = false;

        try {
            while (!outputDone) {
                if (!inputDone) {
                    int inIdx = codec.dequeueInputBuffer(10000);
                    if (inIdx >= 0) {
                        ByteBuffer in = codec.getInputBuffer(inIdx);
                        int size = in == null ? -1 : extractor.readSampleData(in, 0);
                        long ptsUs = extractor.getSampleTime();
                        if (size < 0 || (keep && ptsUs > endUs)) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, ptsUs, 0);
                            extractor.advance();
                        }
                    }
                }

                int outIdx = codec.dequeueOutputBuffer(info, 10000);
                if (outIdx >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                    boolean inRange = info.presentationTimeUs >= startUs
                            && info.presentationTimeUs <= endUs;
                    if (info.size > 0 && (keep == inRange)) {
                        ByteBuffer outBuf = codec.getOutputBuffer(outIdx);
                        if (outBuf != null) {
                            byte[] chunk = new byte[info.size];
                            outBuf.position(info.offset);
                            outBuf.get(chunk);
                            os.write(chunk);
                            pcmBytes += chunk.length;
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false);
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                }
            }
        } finally {
            try {
                codec.stop();
                codec.release();
            } catch (Exception ignored) {
            }
            os.close();
        }
        patchWavHeader(out, sampleRate, channels, pcmBytes);
    }

    private static void writeWavHeader(OutputStream os, int sampleRate, int channels, long pcmBytes)
            throws IOException {
        int byteRate = sampleRate * channels * 2;
        os.write(new byte[]{'R', 'I', 'F', 'F'});
        writeInt(os, (int) (36 + pcmBytes));
        os.write(new byte[]{'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
        writeInt(os, 16);
        writeShort(os, (short) 1);
        writeShort(os, (short) channels);
        writeInt(os, sampleRate);
        writeInt(os, byteRate);
        writeShort(os, (short) (channels * 2));
        writeShort(os, (short) 16);
        os.write(new byte[]{'d', 'a', 't', 'a'});
        writeInt(os, (int) pcmBytes);
    }

    /** Rewrites the sizes once the real PCM length is known. */
    private static void patchWavHeader(File f, int sampleRate, int channels, long pcmBytes)
            throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "rw");
        try {
            raf.seek(4);
            raf.write(intLE((int) (36 + pcmBytes)));
            raf.seek(22);
            raf.write(shortLE((short) channels));
            raf.write(intLE(sampleRate));
            raf.write(intLE(sampleRate * channels * 2));
            raf.write(shortLE((short) (channels * 2)));
            raf.seek(40);
            raf.write(intLE((int) pcmBytes));
        } finally {
            raf.close();
        }
    }

    private static byte[] intLE(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private static byte[] shortLE(short v) {
        return new byte[]{(byte) v, (byte) (v >> 8)};
    }

    private static void writeInt(OutputStream os, int v) throws IOException {
        os.write(intLE(v));
    }

    private static void writeShort(OutputStream os, short v) throws IOException {
        os.write(shortLE(v));
    }
}

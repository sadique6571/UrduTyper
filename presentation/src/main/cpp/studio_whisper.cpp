#include <jni.h>
#include <atomic>
#include <algorithm>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>
#include <memory>
#include "whisper.h"

struct Session { whisper_context * ctx = nullptr; std::atomic<bool> cancelled{false}; std::atomic<int> progress{0}; ~Session() { if (ctx) whisper_free(ctx); } };
static Session * session(jlong value) { return reinterpret_cast<Session *>(value); }
static void fail(JNIEnv * env, const char * message) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),message); }
static std::string escaped(const std::string & value) {
    std::string out;
    for (unsigned char c : value) { if (c == '"' || c == '\\') { out += '\\'; out += c; } else if (c < 32) { if (c == '\n' || c == '\r' || c == '\t') out += ' '; } else out += c; }
    return out;
}
extern "C" JNIEXPORT jlong JNICALL Java_me_nomi_urdutyper_presentation_ui_type_studio_WhisperNative_create(JNIEnv * env, jobject) { try { return reinterpret_cast<jlong>(new Session()); } catch (const std::bad_alloc &) { fail(env,"Not enough memory for speech analysis."); return 0; } }
extern "C" JNIEXPORT void JNICALL Java_me_nomi_urdutyper_presentation_ui_type_studio_WhisperNative_cancel(JNIEnv *, jobject, jlong ptr) { session(ptr)->cancelled.store(true); }
extern "C" JNIEXPORT jint JNICALL Java_me_nomi_urdutyper_presentation_ui_type_studio_WhisperNative_progress(JNIEnv *, jobject, jlong ptr) { return session(ptr)->progress.load(); }
extern "C" JNIEXPORT void JNICALL Java_me_nomi_urdutyper_presentation_ui_type_studio_WhisperNative_destroy(JNIEnv *, jobject, jlong ptr) { delete session(ptr); }
extern "C" JNIEXPORT jbyteArray JNICALL Java_me_nomi_urdutyper_presentation_ui_type_studio_WhisperNative_run(JNIEnv * env, jobject, jlong ptr, jstring model, jfloatArray audio, jstring language, jint threads) {
    auto * state = session(ptr);
    try {
        whisper_log_set([](enum ggml_log_level, const char *, void *){},nullptr);
        const char * path = env->GetStringUTFChars(model,nullptr);
        auto params = whisper_context_default_params(); params.use_gpu = false;
        state->ctx = whisper_init_from_file_with_params(path,params);
        env->ReleaseStringUTFChars(model,path);
        if (!state->ctx) { fail(env,"Could not load speech model. Reinstall it or try Tiny."); return nullptr; }
        if (!whisper_is_multilingual(state->ctx)) { fail(env,"Urdu needs a multilingual model, not an English-only model."); return nullptr; }
        if (state->cancelled.load()) { env->ThrowNew(env->FindClass("java/util/concurrent/CancellationException"),"Speech analysis cancelled"); return nullptr; }
        const int length = env->GetArrayLength(audio);
        if (length < 1600 || length > 16000*60) { fail(env,"Select 0.1–60 seconds of voice."); return nullptr; }
        std::vector<float> pcm(length); env->GetFloatArrayRegion(audio,0,length,pcm.data());
        const char * lang_chars = env->GetStringUTFChars(language,nullptr); const std::string lang(lang_chars); env->ReleaseStringUTFChars(language,lang_chars);
        auto p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        p.language = lang.c_str(); p.translate = false; p.no_context = true;
        p.n_threads = std::max(1,std::min(4,static_cast<int>(threads)));
        p.print_realtime = false; p.print_progress = false; p.print_timestamps = false; p.print_special = false;
        p.token_timestamps = true; p.split_on_word = true; p.max_len = 1;
        p.abort_callback = [](void * data) { return static_cast<Session *>(data)->cancelled.load(); }; p.abort_callback_user_data = state;
        p.progress_callback = [](whisper_context *, whisper_state *, int progress, void * data) { static_cast<Session *>(data)->progress.store(progress); }; p.progress_callback_user_data = state;
        const int result = whisper_full(state->ctx,p,pcm.data(),length);
        if (state->cancelled.load()) { env->ThrowNew(env->FindClass("java/util/concurrent/CancellationException"),"Speech analysis cancelled"); return nullptr; }
        if (result != 0) { fail(env,"Speech recognition failed. Try a shorter clip or another model."); return nullptr; }
        std::ostringstream json; json << "[";
        bool first = true;
        for (int i = 0; i < whisper_full_n_segments(state->ctx); ++i) {
            const char * text = whisper_full_get_segment_text(state->ctx,i);
            if (!text || !*text) continue;
            double probability = 0; int count = 0;
            for (int n = 0; n < whisper_full_n_tokens(state->ctx,i); ++n) { auto token = whisper_full_get_token_data(state->ctx,i,n); if (token.id < whisper_token_eot(state->ctx) && std::isfinite(token.p)) { probability += token.p; ++count; } }
            if (!first) json << ","; first = false;
            json << "{\"text\":\"" << escaped(text) << "\",\"startMs\":" << whisper_full_get_segment_t0(state->ctx,i)*10 << ",\"endMs\":" << whisper_full_get_segment_t1(state->ctx,i)*10 << ",\"confidence\":" << (count ? probability/count : 0) << "}";
        }
        json << "]"; auto bytes = json.str();
        auto output = env->NewByteArray(static_cast<jsize>(bytes.size())); env->SetByteArrayRegion(output,0,static_cast<jsize>(bytes.size()),reinterpret_cast<const jbyte *>(bytes.data()));
        state->progress.store(100); return output;
    } catch (const std::bad_alloc &) { fail(env,"Not enough memory. Choose the Tiny model and close other apps."); }
      catch (const std::exception &) { fail(env,"Offline speech processing failed."); }
    return nullptr;
}

package org.cyanogenmod.audiofx.backends;

import org.cyanogenmod.audiofx.Constants;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.util.Log;

import java.io.File;

/**
 * Creates an EffectSet appropriate for the current device
 */
public class EffectsFactory implements IEffectFactory {

    private static final String TAG = "AudioFx-EffectsFactory";

    private static int sBrand = -1; // cached value to not hit io every time we need a new effect

    public EffectSet createEffectSet(Context context, int sessionId,
                                     AudioDeviceInfo currentDevice) {
        EffectSet effects = null;
        int brand = getBrand();

        // dts?
        if (brand == Constants.EFFECT_TYPE_DTS) {
            try {
                effects = new DtsEffects(context, sessionId, currentDevice);
            } catch (Exception e) {
                Log.e(TAG, "Unable to create DTS effects!", e);
                effects = null;
            }
            return effects;
        } else if (brand == Constants.EFFECT_TYPE_MAXXAUDIO) {
            // try MaxxAudio next, this will throw an exception if unavailable
            try {
                effects = new MaxxAudioEffects(sessionId, currentDevice);
            } catch (Exception e) {
                Log.e(TAG, "Unable to create MaxxAudio effects!", e);
                effects = null;
            }
            return effects;
        }

        // if this throws, we're screwed, don't bother to recover. these
        // are the standard effects that every android device must have,
        // and if they don't exist we have bigger problems.
        return new AndroidEffects(sessionId, currentDevice);
    }

    public static int getBrand() {
        if (sBrand == -1) {
            sBrand = getBrandInternal();
        }
        return sBrand;
    }

    private static int getBrandInternal() {
        if (hasDts()) {
            return Constants.EFFECT_TYPE_DTS;
        } else if (hasMaxxAudio()) {
            return Constants.EFFECT_TYPE_MAXXAUDIO;
        }
        return Constants.EFFECT_TYPE_ANDROID;
    }

    private static boolean hasDts() {
        return new File("/system/etc/srs/dts.lic").exists();
    }

    private static boolean hasMaxxAudio() {
        return new File("/system/etc/waves/default.mps").exists();
    }
}
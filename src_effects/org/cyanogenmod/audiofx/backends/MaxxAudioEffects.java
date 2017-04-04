package org.cyanogenmod.audiofx.backends;

import android.media.AudioDeviceInfo;
import android.media.audiofx.AudioEffect;
import android.util.Log;
import org.cyanogenmod.audiofx.Constants;
import org.cyanogenmod.audiofx.activity.MasterConfigControl;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.BitSet;
import java.util.UUID;

class MaxxAudioEffects extends EffectSetWithAndroidEq {
    private static final int MAAP_CENTER_ACTIVE = 150;
    private static final int MAAP_CENTER_GAIN_CENTER = 151;
    private static final int MAAP_IVOLUME_ACTIVE = 20;
    private static final int MAAP_MAXX_3D_ACTIVE = 13;
    private static final int MAAP_MAXX_3D_EFFECT = 10;
    private static final int MAAP_MAXX_3D_LOW_FREQUENCY = 12;
    private static final int MAAP_MAXX_3D_SPAN = 11;
    private static final int MAAP_MAXX_BASS_ACTIVE = 6;
    private static final int MAAP_MAXX_BASS_EFFECT = 4;
    private static final int MAAP_MAXX_BASS_ORIGINAL_BASS = 37;
    private static final int MAAP_MAXX_HF_ACTIVE = 9;
    private static final int MAAP_MAXX_HF_EFFECT = 7;
    private static final int MAXXBASS = 0;
    private static final int MAXXSPACE = 2;
    private static final int MAXXTREBLE = 1;
    private static final int MAXXVOLUME = 3;
    private static final short PRESET_BLUETOOTH = (short) 6;
    private static final short PRESET_CAST = (short) 6;
    private static final short PRESET_HEADSET = (short) 2;
    private static final short PRESET_LINE_OUT = (short) 6;
    private static final short PRESET_SPEAKER = (short) 1;
    private static final short PRESET_USB = (short) 6;
    private static final String TAG = "AudioFx-MaxxAudio";
    private int mBypassed = -1;
    private MaxxEffect mMaxxEffect;
    private final BitSet mSubEffectBits = new BitSet();

    static class MaxxEffect extends AudioEffect {
        private static final int SL_CMD_WAVESFX_AF_DEVICE_DISABLE = 65562;
        private static final int SL_CMD_WAVESFX_AF_DEVICE_ENABLE = 65561;
        private static final int SL_CMD_WAVESFX_CLEAR_PARAMETERS = 65545;
        private static final int SL_CMD_WAVESFX_PRESET_GET_PARAMETER = 65550;
        private static final int SL_CMD_WAVESFX_PRESET_SET_PARAMETER = 65549;
        private static final int SL_CMD_WAVESFX_SET_OUTDEVICE = 65544;
        private static final int SL_CMD_WAVESFX_SET_SMOOTHING = 65558;
        private static final int SL_CMD_WAVESFX_SET_SOUNDMODE = 65539;
        private static final short SOUNDMODE_MUSIC = (short) 2;
        private static final short SOUNDMODE_RINGTONE = (short) 0;
        private static final short SOUNDMODE_VOICE = (short) 1;

        public MaxxEffect(int priority, int audioSession) throws RuntimeException {
            super(UUID.fromString("a122acc0-5943-11e0-acd3-0002a5d5c51b"), EFFECT_TYPE_NULL, priority, audioSession);
            setDeviceDetectionEnabled(false);
        }

        public int setEnabled(boolean enabled) throws IllegalStateException {
            return super.setEnabled(enabled);
        }

        private int setDeviceDetectionEnabled(boolean enabled) {
            byte[] ret = new byte[MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT];
            checkStatus(command(enabled ? SL_CMD_WAVESFX_AF_DEVICE_ENABLE : SL_CMD_WAVESFX_AF_DEVICE_DISABLE, new byte[MaxxAudioEffects.MAXXBASS], ret));
            return byteArrayToInt(ret);
        }

        public int setOutputMode(short outputMode) {
            byte[] ret = new byte[MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT];
            checkStatus(command(SL_CMD_WAVESFX_SET_OUTDEVICE, shortToByteArray(outputMode), ret));
            return byteArrayToInt(ret);
        }

        public int setSoundMode(short soundMode) {
            byte[] ret = new byte[MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT];
            checkStatus(command(SL_CMD_WAVESFX_SET_SOUNDMODE, shortToByteArray(soundMode), ret));
            return byteArrayToInt(ret);
        }

        public int setBypass(boolean bypass) {
            int i = MaxxAudioEffects.MAXXTREBLE;
            byte[] ret = new byte[MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT];
            byte[] cmd = new byte[MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT];
            System.arraycopy(shortToByteArray(SOUNDMODE_VOICE), MaxxAudioEffects.MAXXBASS, cmd, MaxxAudioEffects.MAXXBASS, MaxxAudioEffects.MAXXSPACE);
            if (!bypass) {
                i = MaxxAudioEffects.MAXXBASS;
            }
            System.arraycopy(shortToByteArray((short) i), MaxxAudioEffects.MAXXBASS, cmd, MaxxAudioEffects.MAXXSPACE, MaxxAudioEffects.MAXXSPACE);
            checkStatus(command(SL_CMD_WAVESFX_SET_SMOOTHING, cmd, ret));
            return byteArrayToInt(ret);
        }

        public int clearUserParameters() {
            byte[] ret = new byte[MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT];
            checkStatus(command(SL_CMD_WAVESFX_CLEAR_PARAMETERS, new byte[MaxxAudioEffects.MAXXBASS], ret));
            return byteArrayToInt(ret);
        }

        public int setParameter(int param, double value) {
            return setParameter(intToByteArray(param), doubleToByteArray(value));
        }

        public int setPresetParameter(int param, double value, short outputMode, short soundMode) {
            byte[] cmd = new byte[16];
            byte[] ret = new byte[MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT];
            System.arraycopy(intToByteArray(param), MaxxAudioEffects.MAXXBASS, cmd, MaxxAudioEffects.MAXXBASS, MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT);
            System.arraycopy(doubleToByteArray(value), MaxxAudioEffects.MAXXBASS, cmd, MaxxAudioEffects.MAAP_MAXX_BASS_EFFECT, 8);
            System.arraycopy(shortToByteArray(outputMode), MaxxAudioEffects.MAXXBASS, cmd, MaxxAudioEffects.MAAP_MAXX_3D_LOW_FREQUENCY, MaxxAudioEffects.MAXXSPACE);
            System.arraycopy(shortToByteArray(soundMode), MaxxAudioEffects.MAXXBASS, cmd, 14, MaxxAudioEffects.MAXXSPACE);
            checkStatus(command(SL_CMD_WAVESFX_PRESET_SET_PARAMETER, cmd, ret));
            return byteArrayToInt(ret);
        }

        private byte[] doubleToByteArray(double value) {
            ByteBuffer converter = ByteBuffer.allocate(8);
            converter.order(ByteOrder.nativeOrder());
            converter.putDouble(value);
            return converter.array();
        }
    }

    public MaxxAudioEffects(int sessionId, AudioDeviceInfo deviceInfo) {
        super(sessionId, deviceInfo);
    }

    protected void onCreate() {
        this.mMaxxEffect = new MaxxEffect(100, this.mSessionId);
        this.mMaxxEffect.setSoundMode(PRESET_HEADSET);
        this.mMaxxEffect.setOutputMode(getWavesPresetForDevice(getDevice()));
        super.onCreate();
    }

    public boolean hasTrebleBoost() {
        return true;
    }

    public boolean hasVolumeBoost() {
        return true;
    }

    public boolean hasVirtualizer() {
        return true;
    }

    public boolean hasBassBoost() {
        return true;
    }

    public void enableBassBoost(boolean enable) {
        enableSubEffect(MAXXBASS, enable);
    }

    public void setBassBoostStrength(short strength) {
        setParameterSafe(MAAP_MAXX_BASS_EFFECT, ((double) strength) / 10.0d);
    }

    public void enableVirtualizer(boolean enable) {
        enableSubEffect(MAXXSPACE, enable);
    }

    public void setVirtualizerStrength(short strength) {
        setParameterSafe(MAAP_MAXX_3D_EFFECT, ((double) strength) / 10.0d);
    }

    public void enableTrebleBoost(boolean enable) {
        enableSubEffect(MAXXTREBLE, enable);
    }

    public void setTrebleBoostStrength(short strength) {
        setParameterSafe(MAAP_MAXX_HF_EFFECT, (double) strength);
    }

    public void enableVolumeBoost(boolean enable) {
        enableSubEffect(MAXXVOLUME, enable);
    }

    public int getBrand() {
        return MAXXSPACE;
    }

    public synchronized void setGlobalEnabled(boolean globalEnabled) {
        int bypass = globalEnabled ? MAXXBASS : MAXXTREBLE;
        try {
            if (this.mBypassed != bypass) {
                if (!globalEnabled) {
                    this.mMaxxEffect.clearUserParameters();
                }
                this.mMaxxEffect.setBypass(!globalEnabled);
                this.mBypassed = bypass;
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to set effects enabled!", e);
        }
        super.setGlobalEnabled(globalEnabled);
    }

    public boolean beginUpdate() {
        return this.mMaxxEffect != null ? super.beginUpdate() : false;
    }

    public boolean commitUpdate() {
        if (this.mMaxxEffect == null) {
            return false;
        }
        updateDeviceSpecificParameters();
        return super.commitUpdate();
    }

    public int getReleaseDelay() {
        return 8000;
    }

    public synchronized void setDevice(AudioDeviceInfo deviceInfo) {
        super.setDevice(deviceInfo);
        this.mSubEffectBits.clear();
        try {
            this.mMaxxEffect.clearUserParameters();
            this.mMaxxEffect.setOutputMode(getWavesPresetForDevice(deviceInfo));
        } catch (Exception e) {
            Log.e(TAG, "Error sending WAVESFX commands!", e);
        }
    }

    public synchronized void release() {
        super.release();
        if (this.mMaxxEffect != null) {
            try {
                this.mMaxxEffect.setEnabled(false);
            } catch (Exception e) {
                Log.e(TAG, "Error disabling MaxxEffects!", e);
            }
            try {
                this.mMaxxEffect.release();
            } catch (Exception e2) {
                Log.e(TAG, "Error releasing MaxxEffects!", e2);
            }
            this.mMaxxEffect = null;
        }
    }

    private short getWavesPresetForDevice(AudioDeviceInfo info) {
        String dev = MasterConfigControl.getDeviceIdentifierString(info);
        if (dev.equals(Constants.DEVICE_HEADSET)) {
            return PRESET_HEADSET;
        }
        if (dev.equals(Constants.DEVICE_LINE_OUT) || dev.startsWith(Constants.DEVICE_PREFIX_BLUETOOTH) || dev.startsWith(Constants.DEVICE_PREFIX_CAST) || dev.startsWith(Constants.DEVICE_PREFIX_USB)) {
            return PRESET_USB;
        }
        return PRESET_SPEAKER;
    }

    private void updateDeviceSpecificParameters() {
        double d;
        double d2 = 1.0d;
        double d3 = 0.0d;
        boolean smallSpeakers = getDevice() != null ? getDevice().getType() == MAXXSPACE : true;
        boolean originalBass = (this.mSubEffectBits.get(MAXXBASS) && smallSpeakers) ? false : true;
        if (originalBass) {
            d = 1.0d;
        } else {
            d = 0.0d;
        }
        setParameterSafe(MAAP_MAXX_BASS_ORIGINAL_BASS, d);
        if (!smallSpeakers) {
            d3 = 6.0d;
        }
        setParameterSafe(MAAP_MAXX_3D_LOW_FREQUENCY, d3);
        if (!smallSpeakers) {
            d2 = 2.0d;
        }
        setParameterSafe(MAAP_MAXX_3D_SPAN, d2);
    }

    private synchronized boolean setParameterSafe(int param, double value) {
        if (this.mMaxxEffect == null) {
            Log.e(TAG, "MaxxEffect went away!");
            return false;
        }
        try {
            this.mMaxxEffect.setPresetParameter(param, value, getWavesPresetForDevice(getDevice()), PRESET_HEADSET);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Unable to set parameter + " + param + " value=" + value, e);
            return false;
        }
    }

    private boolean enableSubEffect(int type, boolean enable) {
        int param = -1;
        switch (type) {
            case MAXXBASS /*0*/:
                param = MAAP_MAXX_BASS_ACTIVE;
                break;
            case MAXXTREBLE /*1*/:
                param = MAAP_MAXX_HF_ACTIVE;
                break;
            case MAXXSPACE /*2*/:
                param = MAAP_MAXX_3D_ACTIVE;
                break;
            case MAXXVOLUME /*3*/:
                param = MAAP_IVOLUME_ACTIVE;
                break;
        }
        if (param < 0) {
            return false;
        }
        if (!setParameterSafe(param, enable ? 1.0d : 0.0d)) {
            return false;
        }
        this.mSubEffectBits.set(type, enable);
        return true;
    }
}

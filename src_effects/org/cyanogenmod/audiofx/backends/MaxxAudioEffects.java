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

        @Override
        public int setEnabled(boolean enabled) throws IllegalStateException {
            return super.setEnabled(enabled);
        }

        private int setDeviceDetectionEnabled(boolean enabled) {
            byte[] ret = new byte[4];
            checkStatus(command(enabled ? SL_CMD_WAVESFX_AF_DEVICE_ENABLE : SL_CMD_WAVESFX_AF_DEVICE_DISABLE, new byte[0], ret));
            return byteArrayToInt(ret);
        }

        public int setOutputMode(short outputMode) {
            byte[] ret = new byte[4];
            checkStatus(command(SL_CMD_WAVESFX_SET_OUTDEVICE, shortToByteArray(outputMode), ret));
            return byteArrayToInt(ret);
        }

        public int setSoundMode(short soundMode) {
            byte[] ret = new byte[4];
            checkStatus(command(SL_CMD_WAVESFX_SET_SOUNDMODE, shortToByteArray(soundMode), ret));
            return byteArrayToInt(ret);
        }

        public int setBypass(boolean bypass) {
            int i = 1;
            byte[] ret = new byte[4];
            byte[] cmd = new byte[4];
            System.arraycopy(shortToByteArray(SOUNDMODE_VOICE), 0, cmd, 0, 2);
            if (!bypass) {
                i = 0;
            }
            System.arraycopy(shortToByteArray((short) i), 0, cmd, 2, 2);
            checkStatus(command(SL_CMD_WAVESFX_SET_SMOOTHING, cmd, ret));
            return byteArrayToInt(ret);
        }

        public int clearUserParameters() {
            byte[] ret = new byte[4];
            checkStatus(command(SL_CMD_WAVESFX_CLEAR_PARAMETERS, new byte[0], ret));
            return byteArrayToInt(ret);
        }

        public int setParameter(int param, double value) {
            return setParameter(intToByteArray(param), doubleToByteArray(value));
        }

        public int setPresetParameter(int param, double value, short outputMode, short soundMode) {
            byte[] cmd = new byte[16];
            byte[] ret = new byte[4];

            System.arraycopy(intToByteArray(param), 0, cmd, 0, 4);
            System.arraycopy(doubleToByteArray(value), 0, cmd, 4, 8);
            System.arraycopy(shortToByteArray(outputMode), 0, cmd, 12, 2);
            System.arraycopy(shortToByteArray(soundMode), 0, cmd, 14, 2);
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

    @Override
    protected void onCreate() {
        this.mMaxxEffect = new MaxxEffect(100, this.mSessionId);
        this.mMaxxEffect.setSoundMode(PRESET_HEADSET);
        this.mMaxxEffect.setOutputMode(getWavesPresetForDevice(getDevice()));
        super.onCreate();
    }

    @Override
    public boolean hasTrebleBoost() {
        return true;
    }

    @Override
    public boolean hasVolumeBoost() {
        return true;
    }

    @Override
    public boolean hasVirtualizer() {
        return true;
    }

    @Override
    public boolean hasBassBoost() {
        return true;
    }

    @Override
    public void enableBassBoost(boolean enable) {
        enableSubEffect(MAXXBASS, enable);
    }

    @Override
    public void setBassBoostStrength(short strength) {
        setParameterSafe(MAAP_MAXX_BASS_EFFECT, ((double) strength) / 10.0);
    }

    @Override
    public void enableVirtualizer(boolean enable) {
        enableSubEffect(MAXXSPACE, enable);
    }

    @Override
    public void setVirtualizerStrength(short strength) {
        setParameterSafe(MAAP_MAXX_3D_EFFECT, ((double) strength) / 10.0);
    }

    @Override
    public void enableTrebleBoost(boolean enable) {
        enableSubEffect(MAXXTREBLE, enable);
        setParameterSafe(MAAP_CENTER_ACTIVE, enable ? 1.0 : 0.0);
    }

    @Override
    public void setTrebleBoostStrength(short strength) {
        setParameterSafe(MAAP_MAXX_HF_EFFECT, (double) strength);
        setParameterSafe(MAAP_CENTER_GAIN_CENTER, ((double) strength));
    }

    @Override
    public void enableVolumeBoost(boolean enable) {
        enableSubEffect(MAXXVOLUME, enable);
    }

    @Override
    public int getBrand() {
        return Constants.EFFECT_TYPE_MAXXAUDIO;
    }

    @Override
    public synchronized void setGlobalEnabled(boolean globalEnabled) {
        int bypass = globalEnabled ? 0 : 1;
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

    @Override
    public boolean beginUpdate() {
        return this.mMaxxEffect != null && super.beginUpdate();
    }

    @Override
    public boolean commitUpdate() {
        if (this.mMaxxEffect == null) {
            return false;
        }
        updateDeviceSpecificParameters();
        return super.commitUpdate();
    }

    @Override
    public int getReleaseDelay() {
        return 8000;
    }

    @Override
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

    @Override
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
            return PRESET_LINE_OUT;
        }
        return PRESET_SPEAKER;
    }

    private void updateDeviceSpecificParameters() {
        double d;
        double d2 = 1.0;
        double d3 = 0.0;
        boolean smallSpeakers = getDevice() != null && getDevice().getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER;
        boolean originalBass = (this.mSubEffectBits.get(MAXXBASS) && smallSpeakers) ? false : true;
        if (originalBass) {
            d = 1.0;
        } else {
            d = 0.0;
        }
        setParameterSafe(MAAP_MAXX_BASS_ORIGINAL_BASS, d);
        if (!smallSpeakers) {
            d3 = 6.0;
        }
        setParameterSafe(MAAP_MAXX_3D_LOW_FREQUENCY, d3);
        if (!smallSpeakers) {
            d2 = 2.0;
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
            case MAXXBASS:
                param = MAAP_MAXX_BASS_ACTIVE;
                break;
            case MAXXTREBLE:
                param = MAAP_MAXX_HF_ACTIVE;
                break;
            case MAXXSPACE:
                param = MAAP_MAXX_3D_ACTIVE;
                break;
            case MAXXVOLUME:
                param = MAAP_IVOLUME_ACTIVE;
                break;
        }
        if (param < 0) {
            return false;
        }
        if (!setParameterSafe(param, enable ? 1.0 : 0.0)) {
            return false;
        }
        this.mSubEffectBits.set(type, enable);
        return true;
    }
}

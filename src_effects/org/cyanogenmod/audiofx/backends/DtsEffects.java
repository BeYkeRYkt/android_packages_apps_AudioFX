package org.cyanogenmod.audiofx.backends;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.util.Pair;

import org.cyanogenmod.audiofx.Constants;
import org.cyanogenmod.audiofx.eq.EqUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class DtsEffects extends EffectSet {
    private static final int[] CENTER_FREQS = new int[]{31000, 62000, 125000, 250000, 500000, 1000000, 2000000, 4000000, 8000000, 16000000};
    private static final boolean DEBUG = false;
    private static final int EQUALIZER_BANDS_TO_USE = 10;
    private static final short[] EQUALIZER_BAND_LEVEL_RANGE = new short[]{(short) -1500, (short) 1500};
    private static final String[] GEQ_BANKS = new String[]{"srs_geq_0_int", "srs_geq_0_ext"};
    private static final String GEQ_DEF_GAINS = "geq_defgains";
    private static final String GEQ_EXT_ENABLE = "geq_ext_enable";
    private static final String GEQ_EXT_PRESET = "geq_ext_preset";
    private static final String GEQ_INT_ENABLE = "geq_int_enable";
    private static final String GEQ_INT_PRESET = "geq_int_preset";
    private static final String GEQ_USER_GAINS = "geq_usergains";
    private static final String TAG = "DtsEffects";
    private static final String[] TRUMEDIA_BANKS = new String[]{"srs_cfg"};
    private static final String TRUMEDIA_DEFER_SAVE = "srs_processing_defersave";
    private static final String TRUMEDIA_ENABLE = "trumedia_enable";
    private static final String[] WOWHDX_BANKS = new String[]{"srs_mus_int", "srs_mov_int", "srs_pod_int", "srs_mus_ext", "srs_pod_ext"};
    private static final String WOWHD_DEFINITION_ENABLE = "wowhd_definition_enable";
    private static final String WOWHD_DEFINITION_MIN = "wowhd_definition_min";
    private static final String WOWHD_DEFINITION_SLIDE = "wowhd_definition_slide";
    private static final String WOWHD_DEFINITION_WINDOW = "wowhd_definition_window";
    private static final String WOWHD_SKIP = "wowhd_skip";
    private static final String WOWHD_SRS_ENABLE = "wowhd_srs_enable";
    private static final String WOWHD_SRS_SPACE = "wowhd_srs_space";
    private static final String WOWHD_SRS_SPEAKER = "wowhd_srs_speaker";
    private static final String WOWHD_TRUBASS_COMPENSATOR = "wowhd_trubass_compressor";
    private static final String WOWHD_TRUBASS_ENABLE = "wowhd_trubass_enable";
    private static final String WOWHD_TRUBASS_FREQ = "wowhd_trubass_freq";
    private static final String WOWHD_TRUBASS_MIN = "wowhd_trubass_min";
    private static final String WOWHD_TRUBASS_MODE = "wowhd_trubass_mode";
    private static final String WOWHD_TRUBASS_SLIDE = "wowhd_trubass_slide";
    private static final String WOWHD_TRUBASS_WINDOW = "wowhd_trubass_window";
    private AudioManager mAm;
    private ParamBuilder mBuilder;
    private final Context mContext;
    final short[] mEqualizer = new short[getNumEqualizerBands()];

    private static class ParamBuilder {
        private static final int MSG_DO_DELAYED_DELTA_PUSH = 1;
        private final Map<String, String> mAppliedEffects = new HashMap();
        AudioManager mAudioManager;
        Handler mHandler = new Handler(new Callback() {
            public boolean handleMessage(Message msg) {
                String command = null;
                switch (msg.what) {
                    case ParamBuilder.MSG_DO_DELAYED_DELTA_PUSH /*1*/:
                        synchronized (ParamBuilder.this.mPendingEffects) {
                            command = ParamBuilder.buildCommand(ParamBuilder.this.mPendingEffects);
                            ParamBuilder.this.mAppliedEffects.putAll(ParamBuilder.this.mPendingEffects);
                            ParamBuilder.this.mPendingEffects.clear();
                        }
                        break;
                }
                if (!(command == null || command.isEmpty())) {
                    ParamBuilder.this.mAudioManager.setParameters(command);
                }
                return true;
            }
        });
        private final Map<String, String> mPendingEffects = new HashMap();

        public ParamBuilder(AudioManager am) {
            this.mAudioManager = am;
        }

        private static String buildCommand(Map<String, String> commands) {
            StringBuilder params = new StringBuilder();
            for (Entry<String, String> param : commands.entrySet()) {
                params.append((String) param.getKey()).append("=").append((String) param.getValue()).append(";");
            }
            return params.toString();
        }

        public static String formatValue(Object value) {
            if (value.getClass().isAssignableFrom(Boolean.class)) {
                value = ((Boolean) value).booleanValue() ? "1" : "0";
            } else if (value.getClass().isAssignableFrom(Float.class)) {
                Object[] objArr = new Object[MSG_DO_DELAYED_DELTA_PUSH];
                objArr[0] = (Float) value;
                value = String.format("%.2f", objArr);
            }
            return String.valueOf(value);
        }

        public ParamBuilder addParam(String key, Object value, String... banks) {
            String strValue = formatValue(value);
            synchronized (this.mPendingEffects) {
                if (banks != null) {
                    int length = banks.length;
                    for (int i = 0; i < length; i += MSG_DO_DELAYED_DELTA_PUSH) {
                        String fKey = banks[i] + ":" + key;
                        if (valueChangedLocked(fKey, strValue)) {
                            this.mPendingEffects.put(fKey, strValue);
                        }
                    }
                } else if (valueChangedLocked(key, strValue)) {
                    this.mPendingEffects.put(key, strValue);
                }
            }
            return this;
        }

        private boolean valueChangedLocked(String key, String value) {
            return (this.mAppliedEffects.containsKey(key) && ((String) this.mAppliedEffects.get(key)).equals(value)) ? DtsEffects.DEBUG : true;
        }

        public ParamBuilder push() {
            if (!this.mHandler.hasMessages(MSG_DO_DELAYED_DELTA_PUSH)) {
                this.mHandler.sendEmptyMessage(MSG_DO_DELAYED_DELTA_PUSH);
            }
            return this;
        }
    }

    public DtsEffects(Context context, int sessionId, AudioDeviceInfo deviceInfo) {
        super(sessionId, deviceInfo);
        this.mContext = context;
    }

    protected void onCreate() {
        int i;
        this.mAm = (AudioManager) this.mContext.getSystemService("audio");
        this.mBuilder = new ParamBuilder(this.mAm);
        boolean speaker = getDevice().getType() == 2 ? true : DEBUG;
        ParamBuilder paramBuilder = this.mBuilder;
        String str = WOWHD_TRUBASS_MODE;
        if (speaker) {
            i = 0;
        } else {
            i = 1;
        }
        paramBuilder = paramBuilder.addParam(str, Integer.valueOf(i), WOWHDX_BANKS);
        str = WOWHD_SRS_SPEAKER;
        if (speaker) {
            i = 0;
        } else {
            i = 1;
        }
        paramBuilder.addParam(str, Integer.valueOf(i), WOWHDX_BANKS).push();
        this.mBuilder.addParam(TRUMEDIA_DEFER_SAVE, Boolean.valueOf(true), new String[0]);
        this.mBuilder.addParam(WOWHD_SKIP, Boolean.valueOf(DEBUG), WOWHDX_BANKS);
        this.mBuilder.addParam(WOWHD_TRUBASS_MIN, Integer.valueOf(0), WOWHDX_BANKS).addParam(WOWHD_TRUBASS_WINDOW, Integer.valueOf(1), WOWHDX_BANKS).addParam(WOWHD_DEFINITION_MIN, Integer.valueOf(0), WOWHDX_BANKS).addParam(WOWHD_DEFINITION_WINDOW, Integer.valueOf(1), WOWHDX_BANKS);
        this.mBuilder.addParam(WOWHD_TRUBASS_ENABLE, Boolean.valueOf(true), WOWHDX_BANKS).addParam(WOWHD_TRUBASS_SLIDE, Integer.valueOf(0), WOWHDX_BANKS).addParam(WOWHD_SRS_ENABLE, Boolean.valueOf(true), WOWHDX_BANKS).addParam(WOWHD_SRS_SPACE, Integer.valueOf(0), WOWHDX_BANKS).addParam(WOWHD_DEFINITION_ENABLE, Boolean.valueOf(true), WOWHDX_BANKS).addParam(WOWHD_DEFINITION_SLIDE, Integer.valueOf(0), WOWHDX_BANKS);
        this.mBuilder.push();
    }

    public int getBrand() {
        return Constants.EFFECT_TYPE_DTS;
    }

    public boolean hasVirtualizer() {
        return true;
    }

    public boolean hasBassBoost() {
        return true;
    }

    public boolean hasTrebleBoost() {
        return true;
    }

    public short getNumEqualizerBands() {
        return (short) 10;
    }

    public void setGlobalEnabled(boolean globalEnabled) {
        this.mBuilder.addParam(TRUMEDIA_ENABLE, Boolean.valueOf(globalEnabled), TRUMEDIA_BANKS).push();
    }

    public void enableEqualizer(boolean enable) {
        this.mBuilder.addParam(GEQ_INT_ENABLE, Boolean.valueOf(enable), TRUMEDIA_BANKS);
        this.mBuilder.addParam(GEQ_INT_PRESET, Integer.valueOf(0), TRUMEDIA_BANKS);
        this.mBuilder.addParam(GEQ_EXT_ENABLE, Boolean.valueOf(enable), TRUMEDIA_BANKS);
        this.mBuilder.addParam(GEQ_EXT_PRESET, Integer.valueOf(0), TRUMEDIA_BANKS);
        this.mBuilder.push();
    }

    public void setEqualizerLevelsDecibels(float[] levels) {
        String value;
        synchronized (this.mEqualizer) {
            for (int i = 0; i < levels.length; i++) {
                this.mEqualizer[i] = (short) ((int) levels[i]);
            }
            value = EqUtils.shortLevelsToString(this.mEqualizer, ",");
        }
        this.mBuilder.addParam(GEQ_USER_GAINS, value, GEQ_BANKS);
        this.mBuilder.push();
    }

    public void setEqualizerBandLevel(short band, float level) {
        String value;
        level /= 100.0f;
        synchronized (this.mEqualizer) {
            this.mEqualizer[band] = (short) ((int) level);
            value = EqUtils.shortLevelsToString(this.mEqualizer, ",");
        }
        this.mBuilder.addParam(GEQ_USER_GAINS, value, GEQ_BANKS);
        this.mBuilder.push();
    }

    public int getEqualizerBandLevel(short band) {
        return this.mEqualizer[band] * 100;
    }

    public String getEqualizerPresetName(short preset) {
        return null;
    }

    public void useEqualizerPreset(short preset) {
    }

    public short getNumEqualizerPresets() {
        return (short) 0;
    }

    public short[] getEqualizerBandLevelRange() {
        return EQUALIZER_BAND_LEVEL_RANGE;
    }

    public int getCenterFrequency(short band) {
        return CENTER_FREQS[band];
    }

    public void enableBassBoost(boolean enable) {
    }

    public void setBassBoostStrength(short strength) {
        this.mBuilder.addParam(WOWHD_TRUBASS_SLIDE, Float.valueOf(((float) strength) / 1000.0f), WOWHDX_BANKS).push();
    }

    public void enableVirtualizer(boolean enable) {
    }

    public void setVirtualizerStrength(short strength) {
        this.mBuilder.addParam(WOWHD_SRS_SPACE, Float.valueOf(((float) strength) / 1000.0f), WOWHDX_BANKS).push();
    }

    public void enableTrebleBoost(boolean enable) {
    }

    public void setTrebleBoostStrength(short strength) {
        this.mBuilder.addParam(WOWHD_DEFINITION_SLIDE, Float.valueOf(((float) strength) / 100.0f), WOWHDX_BANKS).push();
    }

    private Object querySetting(String key, Class type) {
        Pair<String, String> kv = parsePair(this.mAm.getParameters(key));
        if (type.isAssignableFrom(Boolean.class)) {
            return Boolean.valueOf(Integer.parseInt((String) kv.second) == 1 ? true : DEBUG);
        } else if (type.isAssignableFrom(Short.class)) {
            return Short.valueOf(Short.parseShort((String) kv.second));
        } else {
            if (type.isAssignableFrom(Long.class)) {
                return Long.valueOf(Long.parseLong((String) kv.second));
            }
            if (type.isAssignableFrom(Integer.class)) {
                return Integer.valueOf((String) kv.second);
            }
            if (type.isAssignableFrom(String.class)) {
                return kv.second;
            }
            return null;
        }
    }

    private static Pair<String, String> parsePair(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        if (input.endsWith(";")) {
            input = input.substring(0, input.length() - 1);
        }
        String[] split = input.split("=");
        if (split.length != 2) {
            return null;
        }
        return new Pair(split[0], split[1]);
    }
}

package com.awildencounter.client.cutscene;

public record ChromaKey(int rgb, int intensity) {
    public static final int DEFAULT_INTENSITY = 35;
    private static final float LUMA_WEIGHT = 0.35f;
    private static final float MAX_DISTANCE = 150f;
    private static final float SOLID_PART = 0.55f;
    private static final float SPILL_PART = 1.35f;
    private static final float MIN_SPILL_CHROMA = 24f;

    public ChromaKey {
        rgb &= 0xFFFFFF;
        intensity = Math.max(1, Math.min(100, intensity));
    }

    public String hex() {
        return String.format("#%06X", rgb);
    }

    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String value = text.strip();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean apply(int[] argb) {
        Keyer keyer = new Keyer(this);
        boolean see = false;
        for (int i = 0; i < argb.length; i++) {
            argb[i] = keyer.key(argb[i]);
            see |= (argb[i] >>> 24) != 0xFF;
        }
        return see;
    }

    public int apply(int argb) {
        return new Keyer(this).key(argb);
    }

    private static final class Keyer {
        private final float keyY;
        private final float keyCb;
        private final float keyCr;
        private final float outer;
        private final float inner;
        private final float spill;
        private final float keyChroma;

        Keyer(ChromaKey key) {
            int r = (key.rgb >> 16) & 0xFF;
            int g = (key.rgb >> 8) & 0xFF;
            int b = key.rgb & 0xFF;
            keyY = luma(r, g, b);
            keyCb = cb(r, g, b);
            keyCr = cr(r, g, b);
            outer = key.intensity / 100f * MAX_DISTANCE;
            inner = outer * SOLID_PART;
            spill = outer * SPILL_PART;
            keyChroma = (float) Math.sqrt(keyCb * keyCb + keyCr * keyCr);
        }

        int key(int argb) {
            int a = argb >>> 24;
            if (a == 0) {
                return argb;
            }
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            float y = luma(r, g, b);
            float pCb = cb(r, g, b);
            float pCr = cr(r, g, b);
            float dCb = pCb - keyCb;
            float dCr = pCr - keyCr;
            float dY = (y - keyY) * LUMA_WEIGHT;
            float distance = (float) Math.sqrt(dCb * dCb + dCr * dCr + dY * dY);
            if (distance <= inner) {
                return 0;
            }
            if (distance >= spill) {
                return argb;
            }
            float keep = distance >= outer ? 1f : (distance - inner) / (outer - inner);
            if (keyChroma >= MIN_SPILL_CHROMA) {
                float along = (pCb * keyCb + pCr * keyCr) / keyChroma;
                if (along > 0) {
                    float strength = distance >= outer ? (spill - distance) / (spill - outer) : 1f;
                    float remove = along * strength / keyChroma;
                    pCb -= keyCb * remove;
                    pCr -= keyCr * remove;
                    r = clamp(y + 1.402f * pCr);
                    g = clamp(y - 0.344136f * pCb - 0.714136f * pCr);
                    b = clamp(y + 1.772f * pCb);
                }
            }
            int alpha = Math.round(a * keep);
            if (alpha <= 0) {
                return 0;
            }
            return (alpha << 24) | (r << 16) | (g << 8) | b;
        }
    }

    private static float luma(int r, int g, int b) {
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    private static float cb(int r, int g, int b) {
        return -0.168736f * r - 0.331264f * g + 0.5f * b;
    }

    private static float cr(int r, int g, int b) {
        return 0.5f * r - 0.418688f * g - 0.081312f * b;
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    public static int abgrToArgb(int abgr) {
        return (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
    }

    public static int argbToAbgr(int argb) {
        return abgrToArgb(argb);
    }
}

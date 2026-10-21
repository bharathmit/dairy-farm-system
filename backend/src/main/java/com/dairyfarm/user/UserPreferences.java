package com.dairyfarm.user;

/** Per-person app preferences (stored as JSONB on app_user). */
public record UserPreferences(Theme theme, Language language) {

    public enum Theme { LIGHT, DARK, SYSTEM }

    public enum Language { EN, TA }

    public static final UserPreferences DEFAULTS = new UserPreferences(Theme.SYSTEM, Language.EN);

    /** Fills anything missing with the default. */
    public UserPreferences resolved() {
        return new UserPreferences(
                theme != null ? theme : DEFAULTS.theme,
                language != null ? language : DEFAULTS.language);
    }
}

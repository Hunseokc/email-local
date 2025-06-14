package com.dorazibe02.imap.Setting;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum SettingFeature {
    URL_CHECK("urlCheckEnabled", UserSetting::isUrlCheckEnabled, UserSetting::setUrlCheckEnabled),
    NOTION_ENABLED("notionEnabled", UserSetting::isNotionEnabled, UserSetting::setNotionEnabled),
    ALWAYS_DETAILED_SCAN("alwaysDetailedScanEnabled", UserSetting::isAlwaysDetailedScan, UserSetting::setAlwaysDetailedScan);
    // 새로운 기능 추가 시 아래 수정
    // FILE_SCAN("fileScanEnabled", UserSetting::isFileScanEnabled, UserSetting::setFileScanEnabled),
    // HATE_SPEECH_CHECK("hateSpeechCheckEnabled", UserSetting::isHateSpeechCheckEnabled, UserSetting::setHateSpeechCheckEnabled);

    private final String key;
    private final Function<UserSetting, Boolean> getter;
    private final BiConsumer<UserSetting, Boolean> setter;

    SettingFeature(String key, Function<UserSetting, Boolean> getter, BiConsumer<UserSetting, Boolean> setter) {
        this.key = key;
        this.getter = getter;
        this.setter = setter;
    }

    public String getKey() {
        return key;
    }

    public Function<UserSetting, Boolean> getGetter() {
        return getter;
    }

    public BiConsumer<UserSetting, Boolean> getSetter() {
        return setter;
    }
}

package com.sysopbp.engineeringprops;

/** Generates only the two configuration edits supported by the v5 module. */
public final class ToolkitConfig {
    public static String update(String directory, String key, String value) {
        if (!(key.equals("GRAPHICS_PROFILE") || key.equals("GRAPHICS_OVERDRAW"))
                || !(value.equals("0") || value.equals("1"))) {
            throw new IllegalArgumentException("Unsupported config value");
        }
        return "cd " + quote(directory) + " || exit 1; test -f config.conf || { echo 'Toolkit config missing'; exit 1; }; "
                + "cp -p config.conf config.conf.app-backup || exit 1; "
                + "awk 'BEGIN {done=0} /^" + key + "=/ {if (!done) {print \"" + key + "=" + value
                + "\"; done=1}; next} {print} END {if (!done) print \"" + key + "=" + value + "\"}' config.conf > config.conf.app-tmp || exit 1; "
                + "chmod 600 config.conf.app-tmp && mv -f config.conf.app-tmp config.conf || exit 1; "
                + "test \"$(grep '^" + key + "=' config.conf)\" = '" + key + "=" + value + "' || { echo 'Readback mismatch'; exit 1; }; cat config.conf";
    }

    public static String restore(String directory) {
        return "cd " + quote(directory) + " || exit 1; test -f config.conf.app-backup || { echo 'Backup missing'; exit 1; }; "
                + "cp -p config.conf.app-backup config.conf.app-tmp && mv -f config.conf.app-tmp config.conf "
                + "&& echo 'Configuration restored; pending service run or reboot' && cat config.conf";
    }

    private static String quote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }
    private ToolkitConfig() { }
}

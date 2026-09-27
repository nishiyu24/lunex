package com.nishiyu.lunex.mcnet;

public class IPUtils {

    // IP文字列("192.64.1.10")を32bit整数のlong値に変換する
    public static long ipToLong(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) return 0;
        String[] octets = ipAddress.split("\\.");
        if (octets.length != 4) return 0;

        long result = 0;
        try {
            for (int i = 0; i < 4; i++) {
                long octet = Long.parseLong(octets[i]);
                if (octet < 0 || octet > 255) return 0;
                result |= octet << (24 - (8 * i));
            }
        } catch (NumberFormatException e) {
            return 0;
        }
        return result;
    }

    // 32bit整数をIP文字列に戻す
    public static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "." +
                ((ip >> 16) & 0xFF) + "." +
                ((ip >> 8) & 0xFF) + "." +
                (ip & 0xFF);
    }

    // 2つのIPアドレスが、指定されたサブネットマスク上で同じネットワークに属しているか判定する
    public static boolean isSameSubnet(String ip1, String ip2, String subnetMask) {
        long addr1 = ipToLong(ip1);
        long addr2 = ipToLong(ip2);
        long mask = ipToLong(subnetMask);

        if (addr1 == 0 || addr2 == 0 || mask == 0) return false;

        // 論理積(AND演算)によるネットワークアドレスの一致判定
        return (addr1 & mask) == (addr2 & mask);
    }

    // IPアドレス形式として正しいかバリデーション
    public static boolean isValidIp(String ip) {
        return ipToLong(ip) != 0 || "0.0.0.0".equals(ip);
    }
}
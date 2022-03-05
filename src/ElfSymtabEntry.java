public class ElfSymtabEntry {
    public long st_name;
    public long st_value;
    public long st_size;
    public byte st_info;
    public byte st_other;
    public int st_shndex;
    public String name = "";

    public String getBindString() {
        return switch ((st_info & 0xFF) >> 4) {
            case 0 -> "LOCAL";
            case 1 -> "GLOBAL";
            case 2 -> "WEAK";
            default -> {
                if (10 <= ((st_info & 0xFF) >> 4)) {
                    yield "RESERVED";
                }
                yield "";
            }
        };
    }

    public String getTypeString() {
        return switch (st_info & 0xF) {
            case 0 -> "NOTYPE";
            case 1 -> "OBJECT";
            case 2 -> "FUNC";
            case 3 -> "SECTION";
            case 4 -> "FILE";
            case 5 -> "COMMON";
            case 6 -> "TLS";
            default -> {
                if (10 <= (st_info & 0xF)) {
                    yield "RESERVED";
                }
                yield "";
            }
        };
    }

    public String getVisibility() {
        return switch (st_other) {
            case 0 -> "DEFAULT";
            case 1 -> "INTERNAL";
            case 2 -> "HIDDEN";
            case 3 -> "PROTECTED";
            default -> "";
        };
    }

    public String getIndexString() {
        return switch (st_shndex) {
            case 0x0 -> "UNDEF";
            case 0xfff1 -> "ABS";
            case 0xfff2 -> "COMMON";
            case 0xffff -> "XINDEX";
            default -> {
                if (0xff00 <= st_shndex && st_shndex <= 0xffff) {
                    yield "RESERVED";
                }
                yield Integer.toString(st_shndex);
            }
        };
    }

    @Override
    public String toString() {
        return String.format(
                "0x%-15x %5d %-8s %-8s %-8s %6s %s",
                st_value, st_size, getTypeString(), getBindString(),
                getVisibility(), getIndexString(), name
        );
    }
}

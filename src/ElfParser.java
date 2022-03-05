import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ElfParser {
    public static boolean checkIfElf(byte[] file) {
        return file.length >= 4 && file[0] == 0x7f
                && file[1] == 0x45 && file[2] == 0x4c
                && file[3] == 0x46;
    }

    private static byte[] readBytes(byte[] file, int offset, int size) {
        return Arrays.copyOfRange(file, offset, offset + size);
    }

    private static long toLong(byte[] data) {
        long res = 0;
        for (int i = 0; i < data.length; i++) {
            res |= (long)(data[i] & 0xff) << (8 * i);
        }
        return res;
    }

    public static ElfMetadata parseHeader(byte[] file) {
        ElfMetadata metadata = new ElfMetadata();
        metadata.ei_class = file[0x04];
        metadata.ei_data = file[0x05];
        if (metadata.ei_data != 0x1) {
            throw new AssertionError("Incorrect elf file");
        }
        metadata.e_machine = toLong(readBytes(file, 0x12, 2));
        metadata.e_entry = toLong(readBytes(file, 0x18, 4));
        metadata.e_shoff = toLong(readBytes(file, 0x20, 4));
        metadata.e_shentsize = toLong(readBytes(file, 0x2E, 2));
        metadata.e_shnum = toLong(readBytes(file, 0x30, 2));
        metadata.e_shstrndx = toLong(readBytes(file, 0x32, 2));
        return metadata;
    }

    private static String getString(byte[] file, int offset, int stringsStartOffset) {
        int pos = stringsStartOffset + offset;
        StringBuilder res = new StringBuilder();
        do {
            res.append((char)file[pos++]);
        } while (pos < file.length && file[pos] != 0x0);
        return res.toString();
    }

    public static List<ElfSectionInfo> parseSectionHeaders(byte[] file, ElfMetadata metadata) {
        List<ElfSectionInfo> sections = new ArrayList<>();
        for (int i = 0; i < metadata.e_shnum; i++) {
            ElfSectionInfo section = new ElfSectionInfo();
            int pos = (int)(metadata.e_shoff + metadata.e_shentsize * i);
            section.sh_name = toLong(readBytes(file, pos, 4));
            section.sh_type = toLong(readBytes(file, pos + 0x04, 4));
            section.sh_addr = toLong(readBytes(file, pos + 0x0c, 4));
            section.sh_offset = toLong(readBytes(file, pos + 0x10, 4));
            section.sh_size = toLong(readBytes(file, pos + 0x14, 4));
            section.sh_entsize = toLong(readBytes(file, pos + 0x24, 4));
            sections.add(section);
        }
        int namesOffset = (int)sections.get((int)metadata.e_shstrndx).sh_offset;
        for (var section : sections) {
            if (section.sh_type == 0x0) {
                section.name = "NULL";
            } else {
                section.name = getString(file, (int)section.sh_name, namesOffset);
            }
        }
        return sections;
    }

    public static List<ElfSymtabEntry> parseSymtab(byte[] file, ElfSectionInfo symtabHeader, int strtabOffset) {
        List<ElfSymtabEntry> symtab = new ArrayList<>();
        for (int i = 0; i < (int)(symtabHeader.sh_size / symtabHeader.sh_entsize); i++) {
            ElfSymtabEntry entry = new ElfSymtabEntry();
            int pos = (int)symtabHeader.sh_offset + 16 * i;
            entry.st_name = toLong(readBytes(file, pos, 4));
            entry.st_value = toLong(readBytes(file, pos + 0x4, 4));
            entry.st_size = toLong(readBytes(file, pos + 0x8, 4));
            entry.st_info = file[pos + 0xC];
            entry.st_other = file[pos + 0xD];
            entry.st_shndex = (int)toLong(readBytes(file, pos + 0xE, 2));
            if (entry.st_name > 0) {
                entry.name = getString(file, (int)entry.st_name, strtabOffset);
            }
            symtab.add(entry);
        }
        return symtab;
    }
}

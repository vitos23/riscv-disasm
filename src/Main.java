import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Incorrect arguments!\nYou should pass input and output files.");
        }
        try {
            byte[] data = Files.readAllBytes(Path.of(args[0]));
            if (!ElfParser.checkIfElf(data)) {
                System.out.println("Input must be elf file!");
                return;
            }
            ElfMetadata header = ElfParser.parseHeader(data);
            if (header.e_machine != 0xf3) {
                System.out.println("Only Risc-V elf files are supported!");
                return;
            }
            if (header.ei_class != 0x1 || header.e_shentsize != 40) {
                System.out.println("Only 32 bit elf files are supported!");
                return;
            }
            List<ElfSectionInfo> sectionHeaders = ElfParser.parseSectionHeaders(data, header);
            ElfSectionInfo textHeader = null;
            ElfSectionInfo symtabHeader = null;
            int strtabOffset = -1;
            for (var section : sectionHeaders) {
                if (section.name.equals(".text")) {
                    textHeader = section;
                } else if (section.name.equals(".symtab")) {
                    symtabHeader = section;
                } else if (section.name.equals(".strtab")) {
                    strtabOffset = (int)section.sh_offset;
                }
            }
            if (textHeader == null) {
                System.out.println("No .text section found in elf file!");
                return;
            }
            if (symtabHeader == null) {
                System.out.println("No .symtab section found in elf file!");
                return;
            }
            if (symtabHeader.sh_entsize != 16) {
                System.out.println("Incorrect elf file! SH_ENTSIZE of .symtab must be equal to 16");
                return;
            }
            if (strtabOffset == -1) {
                System.out.println("No .strtab section found in elf file!");
                return;
            }

            List<ElfSymtabEntry> symtab = ElfParser.parseSymtab(data, symtabHeader, strtabOffset);

            List<AsmCommand> textSectionDisasm = Disasm.disasm(data, textHeader, Utils.symtabToMap(symtab));
            String disasmResult = Utils.disasmToString(textSectionDisasm);

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(args[1])))) {
                writer.write(".text");
                writer.newLine();
                writer.write(disasmResult);
                writer.newLine();
                writer.write(".symtab");
                writer.newLine();
                writer.write(Utils.symtabToString(symtab));
            } catch (IOException e) {
                System.out.println("An error occurred while writing output: " + e.getMessage());
            }
        } catch (IOException e) {
            System.out.println("An error occurred while reading input file: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
        }
    }
}

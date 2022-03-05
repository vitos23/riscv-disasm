import java.util.*;

public class Disasm {
    private final static String[] REG_NAMES = {
            "zero", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
            "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
            "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
    };

    private final static String[] REG_NAMES_RVC = {
            "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5"
    };

    private static byte[] readBytes(byte[] file, int offset, int size) {
        return Arrays.copyOfRange(file, offset, offset + size);
    }

    private static int toInt(byte[] data) {
        int res = 0;
        for (int i = 0; i < data.length; i++) {
            res |= (data[i] & 0xff) << (8 * i);
        }
        return res;
    }

    private static int getSubBits(int a, int from, int to) {
        return (a << (31 - to)) >>> (31 + from - to);
        //return (int) (((long) a & ((1L << (to + 1)) - 1)) >>> from);
    }

    private static int getBimm12(int a) {
        int res = (getSubBits(a, 7, 7) << 11) | (getSubBits(a, 25, 30) << 5)
                | (getSubBits(a, 8, 11) << 1);
        if (getSubBits(a, 31, 31) == 1) {
            res -= (1 << 12);
        }
        return res;
    }

    private static int getIimm12(int a) {
        int res = getSubBits(a, 20, 30);
        if (getSubBits(a, 31, 31) == 1) {
            res -= (1 << 11);
        }
        return res;
    }

    private static int getSimm12(int a) {
        int res = (getSubBits(a, 25, 30) << 5) | getSubBits(a, 7, 11);
        if (getSubBits(a, 31, 31) == 1) {
            res -= (1 << 11);
        }
        return res;
    }

    private static int getJimm20(int a) {
        int res = (getSubBits(a, 21, 30) << 1) | (getSubBits(a, 20, 20) << 11)
                | (getSubBits(a, 12, 19) << 12);
        if (getSubBits(a, 31, 31) == 1) {
            res -= (1 << 20);
        }
        return res;
    }

    private static int getUimm20(int a) {
        return getSubBits(a, 12, 31) << 12;
    }

    private static String convertFenceArgument(int a) {
        String res = "";
        if ((a & 8) > 0) {
            res += "i";
        }
        if ((a & 4) > 0) {
            res += "o";
        }
        if ((a & 2) > 0) {
            res += "r";
        }
        if ((a & 1) > 0) {
            res += "w";
        }
        return res;
    }

    private static String getSymbol(long addr, Map<Long, String> symtab, Set<Long> taggedLines) {
        taggedLines.add(addr);
        if (symtab.containsKey(addr)) {
            return symtab.get(addr);
        }
        return String.format("LOC_%05x", addr);
    }

    private static AsmCommand parseUncompressedCmd(int data, long addr, Map<Long, String> symtab, Set<Long> tagged) {
        AsmCommand cmd = new AsmCommand();
        cmd.address = addr;
        int func3 = getSubBits(data, 12, 14);
        int opcode = getSubBits(data, 2, 6);
        // rv32i and rv32m only
        if (opcode == 0x18) { // conditional branches
            cmd.name = switch (func3) {
                case 0 -> "beq";
                case 1 -> "bne";
                case 4 -> "blt";
                case 5 -> "bge";
                case 6 -> "bltu";
                case 7 -> "bgeu";
                default -> "unknown_command";
            };
            if (!cmd.name.equals("unknown_command")) {
                cmd.args = new String[]{
                        REG_NAMES[getSubBits(data, 15, 19)],
                        REG_NAMES[getSubBits(data, 20, 24)],
                        getSymbol(addr + getBimm12(data), symtab, tagged)
                };
            }
        } else if (opcode == 0x19 && func3 == 0) { // jalr
            cmd.name = "jalr";
            cmd.args = new String[]{
                    REG_NAMES[getSubBits(data, 7, 11)],
                    REG_NAMES[getSubBits(data, 15, 19)],
                    Integer.toString(getIimm12(data))
            };
        } else if (opcode == 0x1b) { // jal
            cmd.name = "jal";
            cmd.args = new String[]{
                    REG_NAMES[getSubBits(data, 7, 11)],
                    getSymbol(addr + getJimm20(data), symtab, tagged)
            };
        } else if (opcode == 0x0d) { // lui
            cmd.name = "lui";
            cmd.args = new String[]{
                    REG_NAMES[getSubBits(data, 7, 11)],
                    Integer.toString(getUimm20(data))
            };
        } else if (opcode == 0x05) { // auipc
            cmd.name = "auipc";
            cmd.args = new String[]{
                    REG_NAMES[getSubBits(data, 7, 11)],
                    Integer.toString(getUimm20(data))
            };
        } else if (opcode == 0x04) { // integer register-immediate
            cmd.name = switch (func3) {
                case 0 -> "addi";
                case 2 -> "slti";
                case 3 -> "sltiu";
                case 4 -> "xori";
                case 6 -> "ori";
                case 7 -> "andi";
                default -> "unknown_command";
            };
            if (!cmd.name.equals("unknown_command")) {
                cmd.args = new String[]{
                        REG_NAMES[getSubBits(data, 7, 11)],
                        REG_NAMES[getSubBits(data, 15, 19)],
                        Integer.toString(getIimm12(data))
                };
            }
            if (func3 == 1 || func3 == 5) {
                if (func3 == 1) {
                    cmd.name = "slli";
                } else if (getSubBits(data, 25, 31) == 0) {
                    cmd.name = "srli";
                } else {
                    cmd.name = "srai";
                }
                String rd = REG_NAMES[getSubBits(data, 7, 11)];
                String rs = REG_NAMES[getSubBits(data, 15, 19)];
                cmd.args = new String[]{
                        rd,
                        rs,
                        Integer.toString(getSubBits(data, 20, 24))
                };
            }
        } else if (opcode == 0x0c) { // integer register-register
            int func7 = getSubBits(data, 25, 31);
            cmd.name = switch (func3) {
                case 0 -> switch (func7) {
                    case 0 -> "add";
                    case 1 -> "mul";
                    case 32 -> "sub";
                    default -> "unknown_command";
                };
                case 1 -> switch (func7) {
                    case 0 -> "sll";
                    case 1 -> "mulh";
                    default -> "unknown_command";
                };
                case 2 -> switch (func7) {
                    case 0 -> "slt";
                    case 1 -> "mulhsu";
                    default -> "unknown_command";
                };
                case 3 -> switch (func7) {
                    case 0 -> "sltu";
                    case 1 -> "mulhu";
                    default -> "unknown_command";
                };
                case 4 -> switch (func7) {
                    case 0 -> "xor";
                    case 1 -> "div";
                    default -> "unknown_command";
                };
                case 5 -> switch (func7) {
                    case 0 -> "srl";
                    case 1 -> "divu";
                    case 32 -> "sra";
                    default -> "unknown_command";
                };
                case 6 -> switch (func7) {
                    case 0 -> "or";
                    case 1 -> "rem";
                    default -> "unknown_command";
                };
                case 7 -> switch (func7) {
                    case 0 -> "and";
                    case 1 -> "remu";
                    default -> "unknown_command";
                };
                default -> "unknown_command";
            };
            if (!cmd.name.equals("unknown_command")) {
                cmd.args = new String[]{
                        REG_NAMES[getSubBits(data, 7, 11)],
                        REG_NAMES[getSubBits(data, 15, 19)],
                        REG_NAMES[getSubBits(data, 20, 24)],
                };
            }
        } else if (opcode == 0x00) { // load
            cmd.name = switch (func3) {
                case 0 -> "lb";
                case 1 -> "lh";
                case 2 -> "lw";
                case 4 -> "lbu";
                case 5 -> "lhu";
                default -> "unknown_command";
            };
            if (!cmd.name.equals("unknown_command")) {
                cmd.args = new String[]{
                        REG_NAMES[getSubBits(data, 7, 11)],
                        String.format("%d(%s)", getIimm12(data), REG_NAMES[getSubBits(data, 15, 19)])
                };
            }
        } else if (opcode == 0x08) { // store
            cmd.name = switch (func3) {
                case 0 -> "sb";
                case 1 -> "sh";
                case 2 -> "sw";
                default -> "unknown_command";
            };
            if (!cmd.name.equals("unknown_command")) {
                cmd.args = new String[]{
                        REG_NAMES[getSubBits(data, 20, 24)],
                        String.format("%d(%s)", getSimm12(data), REG_NAMES[getSubBits(data, 15, 19)])
                };
            }
        } else if (opcode == 0x03 && (func3 == 0 || func3 == 1)) { // fence, fence.i
            if (func3 == 0) {
                cmd.name = "fence";
                cmd.args = new String[]{
                        convertFenceArgument(getSubBits(data, 24, 27)),
                        convertFenceArgument(getSubBits(data, 20, 23))
                };
            } else {
                cmd.name = "fence.i";
            }
        } else if (opcode == 0x1c) { // system commands
            if (func3 == 0) {
                int funct12 = getSubBits(data, 20, 31);
                switch (funct12) {
                    case 0 -> cmd.name = "ecall";
                    case 1 -> cmd.name = "ebreak";
                }
            } else {
                cmd.name = switch (func3) {
                    case 1 -> "csrrw";
                    case 2 -> "csrrs";
                    case 3 -> "csrrc";
                    case 5 -> "csrrwi";
                    case 6 -> "csrrsi";
                    case 7 -> "csrrci";
                    default -> "";
                };
                cmd.args = new String[]{
                        REG_NAMES[getSubBits(data, 7, 11)],
                        Integer.toString(getSubBits(data, 20, 31)),
                        REG_NAMES[getSubBits(data, 15, 19)]
                };
            }
        }
        if (cmd.name.isEmpty()) {
            cmd.name = "unknown_command";
            cmd.args = new String[]{};
        }
        return cmd;
    }

    private static int getNzimm6(int a) {
        int res = getSubBits(a, 2, 6);
        if (getSubBits(a, 12, 12) == 1) {
            res -= (1 << 5);
        }
        return res;
    }

    private static int getImm11(int a) {
        int res = (getSubBits(a, 8, 8) << 10)
                | (getSubBits(a, 9, 10) << 8)
                | (getSubBits(a, 6, 6) << 7)
                | (getSubBits(a, 7, 7) << 6)
                | (getSubBits(a, 2, 2) << 5)
                | (getSubBits(a, 11, 11) << 4)
                | (getSubBits(a, 3, 5) << 1);
        if (getSubBits(a, 12, 12) == 1) {
            res -= (1 << 11);
        }
        return res;
    }

    private static int getImm8(int a) {
        int res = (getSubBits(a, 5, 6) << 6)
                | (getSubBits(a, 2, 2) << 5)
                | (getSubBits(a, 10, 11) << 3)
                | (getSubBits(a, 3, 4) << 1);
        if (getSubBits(a, 12, 12) == 1) {
            res -= (1 << 8);
        }
        return res;
    }

    private static AsmCommand parseCompressedCmd(int data, long addr, Map<Long, String> symtab, Set<Long> tagged) {
        AsmCommand cmd = new AsmCommand();
        cmd.address = addr;

        if (data == 0) {
            cmd.name = "illegal";
            return cmd;
        }

        int func3 = getSubBits(data, 13, 15);
        int opcode = getSubBits(data, 0, 1);

        if (opcode == 0) {
            int imm;
            switch (func3) {
                case 0 -> {
                    cmd.name = "c.addi4spn";
                    imm = (getSubBits(data, 7, 10) << 6)
                            | (getSubBits(data, 11, 12) << 4)
                            | (getSubBits(data, 5, 5) << 3)
                            | (getSubBits(data, 6, 6) << 2);
                    cmd.args = new String[]{
                            REG_NAMES_RVC[getSubBits(data, 2, 4)],
                            "sp",
                            Integer.toString(imm)
                    };
                }
                case 2 -> {
                    cmd.name = "c.lw";
                    imm = (getSubBits(data, 5, 5) << 6)
                            | (getSubBits(data, 10, 12) << 3)
                            | (getSubBits(data, 6, 6) << 2);
                    cmd.args = new String[]{
                            REG_NAMES_RVC[getSubBits(data, 2, 4)],
                            String.format("%d(%s)", imm, REG_NAMES_RVC[getSubBits(data, 7, 9)])
                    };
                }
                case 6 -> {
                    cmd.name = "c.sw";
                    imm = (getSubBits(data, 5, 5) << 6)
                            | (getSubBits(data, 10, 12) << 3)
                            | (getSubBits(data, 6, 6) << 2);
                    cmd.args = new String[]{
                            REG_NAMES_RVC[getSubBits(data, 2, 4)],
                            String.format("%d(%s)", imm, REG_NAMES_RVC[getSubBits(data, 7, 9)])
                    };
                }
            }
        } else if (opcode == 1) {
            int imm;
            switch (func3) {
                case 0 -> {
                    if (getSubBits(data, 2, 15) == 0) {
                        cmd.name = "c.nop";
                    } else {
                        cmd.name = "c.addi";
                        cmd.args = new String[]{
                                REG_NAMES[getSubBits(data, 7, 11)],
                                Integer.toString(getNzimm6(data))
                        };
                    }
                }
                case 1 -> {
                    cmd.name = "c.jal";
                    cmd.args = new String[]{
                            getSymbol(addr + getImm11(data), symtab, tagged)
                    };
                }
                case 2 -> {
                    cmd.name = "c.li";
                    cmd.args = new String[]{
                            REG_NAMES[getSubBits(data, 7, 11)],
                            Integer.toString(getNzimm6(data))
                    };
                }
                case 3 -> {
                    int rd = getSubBits(data, 7, 11);
                    if (rd == 2) {
                        cmd.name = "c.addi16sp";
                        imm = (getSubBits(data, 3, 4) << 7)
                                | (getSubBits(data, 5, 5) << 6)
                                | (getSubBits(data, 2, 2) << 5)
                                | (getSubBits(data, 6, 6) << 4);
                        if (getSubBits(data, 12, 12) == 1) {
                            imm -= (1 << 9);
                        }
                        cmd.args = new String[]{
                                "sp", "sp",
                                Integer.toString(imm)
                        };
                    } else {
                        cmd.name = "c.lui";
                        imm = getNzimm6(data) << 12;
                        cmd.args = new String[]{
                                REG_NAMES[rd],
                                Integer.toString(imm)
                        };
                    }
                }
                case 4 -> {
                    int func2 = getSubBits(data, 10, 11);
                    switch (func2) {
                        case 0 -> {
                            cmd.name = "c.srli";
                            cmd.args = new String[]{
                                    REG_NAMES_RVC[getSubBits(data, 7, 9)],
                                    Integer.toString(getNzimm6(data))
                            };
                        }
                        case 1 -> {
                            cmd.name = "c.srai";
                            cmd.args = new String[]{
                                    REG_NAMES_RVC[getSubBits(data, 7, 9)],
                                    Integer.toString(getNzimm6(data))
                            };
                        }
                        case 2 -> {
                            cmd.name = "c.andi";
                            cmd.args = new String[]{
                                    REG_NAMES_RVC[getSubBits(data, 7, 9)],
                                    Integer.toString(getNzimm6(data))
                            };
                        }
                        case 3 -> {
                            cmd.name = switch (getSubBits(data, 5, 6)) {
                                case 0 -> "c.sub";
                                case 1 -> "c.xor";
                                case 2 -> "c.or";
                                case 3 -> "c.and";
                                default -> "";
                            };
                            cmd.args = new String[]{
                                    REG_NAMES_RVC[getSubBits(data, 7, 9)],
                                    REG_NAMES_RVC[getSubBits(data, 2, 4)]
                            };
                        }
                    }
                }
                case 5 -> {
                    cmd.name = "c.j";
                    cmd.args = new String[]{
                            getSymbol(addr + getImm11(data), symtab, tagged)
                    };
                }
                case 6 -> {
                    cmd.name = "c.beqz";
                    cmd.args = new String[]{
                            REG_NAMES_RVC[getSubBits(data, 7, 9)],
                            getSymbol(addr + getImm8(data), symtab, tagged)
                    };
                }
                case 7 -> {
                    cmd.name = "c.bnez";
                    cmd.args = new String[]{
                            REG_NAMES_RVC[getSubBits(data, 7, 9)],
                            getSymbol(addr + getImm8(data), symtab, tagged)
                    };
                }
            }
        } else if (opcode == 2) {
            switch (func3) {
                case 0 -> {
                    cmd.name = "c.slli";
                    cmd.args = new String[]{
                            REG_NAMES[getSubBits(data, 7, 11)],
                            Integer.toString(getNzimm6(data))
                    };
                }
                case 2 -> {
                    cmd.name = "c.lwsp";
                    int imm = (getSubBits(data, 2, 3) << 6)
                            | (getSubBits(data, 12, 12) << 5)
                            | (getSubBits(data, 4, 6) << 2);
                    cmd.args = new String[]{
                            REG_NAMES[getSubBits(data, 7, 11)],
                            String.format("%d(sp)", imm)
                    };
                }
                case 4 -> {
                    int rs1 = getSubBits(data, 7, 11);
                    int rs2 = getSubBits(data, 2, 6);
                    if (getSubBits(data, 12, 12) == 0) {
                        if (rs2 == 0) {
                            cmd.name = "c.jr";
                            cmd.args = new String[]{
                                    REG_NAMES[rs1]
                            };
                        } else {
                            cmd.name = "c.mv";
                            cmd.args = new String[]{
                                    REG_NAMES[rs1],
                                    REG_NAMES[rs2]
                            };
                        }
                    } else {
                        if (rs1 == 0 && rs2 == 0) {
                            cmd.name = "c.ebreak";
                        } else if (rs2 == 0) {
                            cmd.name = "c.jalr";
                            cmd.args = new String[]{
                                    REG_NAMES[rs1]
                            };
                        } else {
                            cmd.name = "c.add";
                            cmd.args = new String[]{
                                    REG_NAMES[rs1],
                                    REG_NAMES[rs2]
                            };
                        }
                    }
                }
                case 6 -> {
                    cmd.name = "c.swsp";
                    int imm = (getSubBits(data, 7, 8) << 6)
                            | (getSubBits(data, 9, 12) << 2);
                    cmd.args = new String[]{
                            REG_NAMES[getSubBits(data, 2, 6)],
                            String.format("%d(sp)", imm)
                    };
                }
            }
        }
        if (cmd.name.isEmpty()) {
            cmd.name = "unknown_command";
            cmd.args = new String[]{};
        }

        return cmd;
    }

    public static List<AsmCommand> disasm(byte[] file, ElfSectionInfo textHeader, Map<Long, String> symtab) {
        List<AsmCommand> commands = new ArrayList<>();
        Set<Long> taggedLines = new HashSet<>();
        int pos = (int) textHeader.sh_offset;
        while (pos < textHeader.sh_offset + textHeader.sh_size) {
            long address = pos - textHeader.sh_offset + textHeader.sh_addr; // address = pc
            String symbol = symtab.getOrDefault(address, "");
            if ((file[pos] & 0x3) == 3) {
                commands.add(parseUncompressedCmd(toInt(readBytes(file, pos, 4)), address, symtab, taggedLines));
                pos += 4;
            } else {
                commands.add(parseCompressedCmd(toInt(readBytes(file, pos, 2)), address, symtab, taggedLines));
                pos += 2;
            }
            commands.get(commands.size() - 1).symbol = symbol;
        }
        for (var cmd : commands) {
            if (cmd.symbol.isEmpty() && taggedLines.contains(cmd.address)) {
                cmd.symbol = String.format("LOC_%05x", cmd.address);
            }
        }
        return commands;
    }
}

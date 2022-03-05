# RISC-V Disassembler

Simple disassembler for RV32IM and RVC (compressed 32-bits commands).
This tool was made as a homework for a Computer Architecture course.

This tool can disassemble only 32-bits ELF files.

### Usage

To disassemble pass the following parameters:
1. The path to elf file
2. Path to the file to write result to

The result file consists of two parts:
1. disassemble of `.text` section
2. parsed `.symtab` section

It will look as below:
```
.text
00010074        register_fini: addi a5, zero, 0
00010078                       c.beqz a5, LOC_10082
0001007a                       c.lui a0, 65536
0001007c                       addi a0, a0, 894
00010080                       c.j atexit
00010082            LOC_10082: c.jr ra
00010084               _start: auipc gp, 8192
00010088                       addi gp, gp, -964
0001008c                       addi a0, gp, -972
<...>

.symtab
Symbol Value              Size Type     Bind     Vis       Index Name
[   0] 0x0                   0 NOTYPE   LOCAL    DEFAULT   UNDEF 
[   1] 0x10074               0 SECTION  LOCAL    DEFAULT       1 
[   2] 0x11450               0 SECTION  LOCAL    DEFAULT       2 
[   3] 0x114b4               0 SECTION  LOCAL    DEFAULT       3 
[   4] 0x114bc               0 SECTION  LOCAL    DEFAULT       4 
[   5] 0x114c0               0 SECTION  LOCAL    DEFAULT       5 
[   6] 0x118e8               0 SECTION  LOCAL    DEFAULT       6 
[   7] 0x118f4               0 SECTION  LOCAL    DEFAULT       7 
[   8] 0x0                   0 SECTION  LOCAL    DEFAULT       8 
[   9] 0x0                   0 SECTION  LOCAL    DEFAULT       9 
[  10] 0x0                   0 FILE     LOCAL    DEFAULT     ABS __call_atexit.c
<...>
```
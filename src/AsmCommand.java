public class AsmCommand {
    public long address;
    public String symbol = "";
    public String name = "";
    public String[] args = {};

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder();
        if (symbol.isEmpty()) {
            res.append(String.format("%08x %21s %s ", address, "", name));
        } else {
            res.append(String.format("%08x %20s: %s ", address, symbol, name));
        }
        for (int i = 0; i < args.length; i++) {
            res.append(args[i]);
            if (i != args.length - 1) {
                res.append(", ");
            }
        }
        return res.toString();
    }
}

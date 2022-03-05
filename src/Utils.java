import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {
    public static String symtabToString(List<ElfSymtabEntry> symtab) {
        StringBuilder res = new StringBuilder();
        res.append(String.format(
                "%s %-15s %7s %-8s %-8s %-8s %6s %s",
                "Symbol", "Value", "Size", "Type", "Bind", "Vis", "Index", "Name"
        ));
        res.append(System.lineSeparator());
        for (int i = 0; i < symtab.size(); i++) {
            res.append(String.format("[%4d] %s", i, symtab.get(i)));
            res.append(System.lineSeparator());
        }
        return res.toString();
    }

    public static Map<Long, String> symtabToMap(List<ElfSymtabEntry> symtab) {
        Map<Long, String> res = new HashMap<>();
        for (var entry : symtab) {
            if (!entry.name.isEmpty() && entry.st_value != 0 && entry.getTypeString().equals("FUNC")) {
                res.put(entry.st_value, entry.name);
            }
        }
        return res;
    }

    public static String disasmToString(List<AsmCommand> commands) {
        StringBuilder res = new StringBuilder();
        for (var cmd : commands) {
            res.append(cmd.toString());
            res.append(System.lineSeparator());
        }
        return res.toString();
    }
}

package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.List;

public class TableBuilder {
    private List<TableRow> rows;
    private boolean hasHeader;
    public class TableRow {
        private List<String> data;

        public TableRow(Object... items) {
            this.data = new ArrayList<>();
            for (Object item: items) {
                this.data.add(item.toString());
            }
        }

        public List<String> getData() {
            return data;
        }

        public int getSize() {
            return this.data.size();
        }
    }

    public int getMaxSizeInColumn(int columnIndex) {
        int max = Integer.MIN_VALUE;
        for (TableRow row: rows) {
            int size = row.getData().get(columnIndex).length();
            if (size > max) {
                max = size;
            }
        }
        return max;
    }

    private boolean compatible(TableRow row) {
        for (TableRow r: rows) {
            if (r.getSize() != row.getSize()) {
                return false;
            }
        }
        return true;
    }

    public TableBuilder() {
        this.rows = new ArrayList<>();
        this.hasHeader = false;
    }

    public TableBuilder addRow(Object... items) throws Exception {
        TableRow row = new TableRow(items);
        if (compatible(row)) {
            rows.add(row);
        } else {
            throw new Exception("Row is not compatible with other rows!");
        }
        return this;
    }

    public TableBuilder addHeader(Object... items) throws Exception {
        TableRow row = new TableRow(items);
        if (hasHeader) {
            throw new Exception("Table already has header!");
        }

        if (compatible(row)) {
            rows.add(0, row);
            hasHeader = true;
        } else {
            throw new Exception("Row is not compatible with other rows!");
        }
        return this;
    }

    @Override
    public String toString() {
        int numOfColumns = rows.get(0).getSize();
        List<Integer> columnSizes = new ArrayList<>();
        for (int i = 0; i < numOfColumns; i++) {
            columnSizes.add(getMaxSizeInColumn(i) + 2);
        }

        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < rows.size(); i++) {
            if (i == 0) {
                sb.append("+");
                for (int j = 0; j < rows.get(i).getSize(); j++) {
                    sb.append(pad(getString("-", columnSizes.get(j)), columnSizes.get(j)));
                    sb.append("+");
                }
                sb.append("\n");
            }

            sb.append("|");
            for (int j = 0; j < rows.get(i).getSize(); j++) {
                sb.append(pad(" " + rows.get(i).getData().get(j), columnSizes.get(j)));
                sb.append("|");
            }
            sb.append("\n");

            sb.append("+");
            for (int j = 0; j < rows.get(i).getSize(); j++) {
                sb.append(pad(getString(i == 0 ? "=" : "-", columnSizes.get(j)), columnSizes.get(j)));
                sb.append("+");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String getString(String symbol, int size) {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < size; i++) {
            sb.append(symbol);
        }
        return sb.toString();
    }

    public String pad(String text, int size) {
        int diff = size - text.length();
        if (diff > 0) {
            StringBuilder sb = new StringBuilder(text);
            for (int i = 0; i < diff; i++) {
                sb.append(" ");
            }
            return sb.toString();
        }
        return text;
    }
}

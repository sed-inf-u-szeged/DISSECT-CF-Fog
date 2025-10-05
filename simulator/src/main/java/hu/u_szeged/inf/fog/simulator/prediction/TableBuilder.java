package hu.u_szeged.inf.fog.simulator.prediction;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

/**
 * Helper class for building tables with rows and headers.
 */
public class TableBuilder {
    
    private List<TableRow> rows;
    private boolean hasHeader;
    
    /**
     * Represents a row in the table.
     */
    @Getter
    public class TableRow {
        
        private List<String> data;

        /**
         * Constructs a table row with the given items.
         *
         * @param items the items to be in the row
         */
        public TableRow(Object... items) {
            this.data = new ArrayList<>();
            for (Object item : items) {
                this.data.add(item.toString());
            }
        }

        /**
         * Returns the size of the row.
         *
         * @return the size of the row
         */
        public int getSize() {
            return this.data.size();
        }
    }

    /**
     * Returns the maximum size in the specified column.
     *
     * @param columnIndex the index of the column
     */
    public int getMaxSizeInColumn(int columnIndex) {
        int max = Integer.MIN_VALUE;
        for (TableRow row : rows) {
            int size = row.getData().get(columnIndex).length();
            if (size > max) {
                max = size;
            }
        }
        return max;
    }

    /**
     * Checks if the given row is compatible with existing rows.
     *
     * @param row the row to check compatibility
     * @return true if the row is compatible, otherwise false
     */
    private boolean compatible(TableRow row) {
        for (TableRow r : rows) {
            if (r.getSize() != row.getSize()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Constructs a TableBuilder object.
     */
    public TableBuilder() {
        this.rows = new ArrayList<>();
        this.hasHeader = false;
    }

    /**
     * Adds a row to the table.
     *
     * @param items the items to populate the row with
     * @return the TableBuilder instance for method chaining
     */
    public TableBuilder addRow(Object... items) throws Exception {
        TableRow row = new TableRow(items);
        if (compatible(row)) {
            rows.add(row);
        } else {
            throw new Exception("Row is not compatible with other rows!");
        }
        return this;
    }

    /**
     * Adds a header to the table.
     *
     * @param items the items to populate the header with
     * @return the TableBuilder instance for method chaining
     */
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

    /**
     * Generates a string representation of the table.
     */
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

    /**
     * Generates a string consisting of the specified symbol repeated the specified number of times.
     *
     * @param symbol the symbol to repeat
     * @param size the number of times to repeat the symbol
     * @return the string consisting of the repeated symbol
     */
    public String getString(String symbol, int size) {
        StringBuilder sb = new StringBuilder("");
        for (int i = 0; i < size; i++) {
            sb.append(symbol);
        }
        return sb.toString();
    }

    /**
     * Pads the given text with spaces to the specified size.
     *
     * @param text the text to pad
     * @param size the desired size of the padded text
     * @return the padded text
     */
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
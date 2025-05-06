package hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite;

import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.Instant;

public class SqLiteManager {

    private static File databaseFile;

    private static Connection connection;

    public SqLiteManager() {

    }

    public static Connection getConnection() {
        if (connection == null) {
            String driverString = "jdbc:sqlite:";
            try {
                connection = DriverManager.getConnection(
                        new StringBuilder(driverString).append(databaseFile.getAbsolutePath()).toString()
                );
            } catch (SQLException e) {
                PredictionLogger.error("SQLite error", "Failed to get connection");
            }
        }

        return connection;
    }

    public File getDatabaseFile() throws SQLException, IOException {
        if (!databaseFile.exists()) {
            setUpDatabase();
        }

        return databaseFile;
    }

    public void setUpDatabase() throws IOException, SQLException {
        databaseFile = createDatabaseFile();

        connection = getConnection();


        getConnection().setAutoCommit(false);
    }

    public static void createTable(String name) {
        try (Statement statement = getConnection().createStatement()) {
            String createRawTableSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                    .append(formatFeatureNameToSQL(name)).append("_feature_raw")
                    .append("(id INTEGER PRIMARY KEY AUTOINCREMENT, raw_value REAL NOT NULL);")
                    .toString();

            String createPredictedTableSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                    .append(formatFeatureNameToSQL(name)).append("_feature_predicted")
                    .append("(id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            " data TEXT NOT NULL)")
                    .toString();

            statement.execute(createRawTableSQL);
            statement.execute(createPredictedTableSQL);

            connection.commit();
        } catch (SQLException e) {
            PredictionLogger.error("SQLite table creation failed", "Failed to create table");
        }
    }

    public static void addDataToTable(String featureName, Double data) {
        String insertSQL = new StringBuilder(" INSERT INTO ")
                .append(formatFeatureNameToSQL(featureName)).append("_feature_raw (raw_value) VALUES ( ? );")
                .toString();
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(insertSQL)) {
            preparedStatement.setDouble(1, data);
            preparedStatement.execute();
            getConnection().commit();
        } catch (SQLException e) {
            PredictionLogger.error("SQLite Insert error", e.getMessage());
        }
    }

    private File createDatabaseFile() throws IOException {
        PredictionLogger.info("SQLite setup", "Creating database...");

        String databasesDirPath = new StringBuilder(
                new File(System.getProperty("user.dir")).getParentFile().getAbsolutePath()
        ).append(File.separator).append("databases").toString();

        File databaseDir = new File(databasesDirPath);
        if (!databaseDir.exists()) {
            if (!databaseDir.mkdir()) {
                PredictionLogger.error("SQLite setup failed", "Failed to create database directory");
                throw new IOException("Failed to create database directory");
            }
        }

        File databaseFile = new File(
                new StringBuilder(databasesDirPath).append(File.separator).append(Instant.now().toString().replace(":", "-")).append(".db").toString()
        );

        if (!databaseFile.createNewFile()) {
            PredictionLogger.error("SQLite setup failed", "Failed to create database file");
            throw new IOException("Failed to create database file");
        }

        PredictionLogger.info("SQLite setup", "Database created");

        return databaseFile;
    }

    private static String formatFeatureNameToSQL(String name) {
        return name.replace(":", "__");
    }
}


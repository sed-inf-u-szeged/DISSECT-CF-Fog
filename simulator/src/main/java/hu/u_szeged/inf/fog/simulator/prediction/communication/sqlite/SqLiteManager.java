package hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.Setter;
import org.json.JSONException;
import org.json.JSONObject;

//import java.sql.*;

/**
 * The class that provides implementation for the handling of the SQLite database.
 * Provides methods for database setup, creating tables, and writing data to the tables.
 */
public class SqLiteManager {

    private static File databaseFile;

    private static Connection connection;

    @Setter
    private static boolean enabled;

    public SqLiteManager() {
        SqLiteManager.setEnabled(PredictionConfigurator.CREATE_DATABASE);
    }

    /**
     * Returns the Connection if present. If not then creates it's instance.
     *
     * @return The Connection object for the database.
     */
    public static Connection getConnection() {
        if (!enabled) {
            return null;
        }
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

    /**
     * Returns the database file as a File object if present. If not then creates it.
     *
     * @return The database file as a File object.
     * @throws SQLException If failed to create the database.
     * @throws IOException If couldn't create the file for the database.
     */
    public File getDatabaseFile() throws SQLException, IOException {
        if (!enabled) {
            return null;
        }
        
        if (!databaseFile.exists()) {
            setUpDatabase();
        }

        return databaseFile;
    }

    /**
     * Creates the database file, then the connection and turns auto commit to false.
     *
     * @throws IOException if couldn't create database file
     * @throws SQLException if there was error with either creating the database, or the Connection for it.
     */
    public void setUpDatabase() throws IOException, SQLException {
        if (!enabled) {
            return;
        }
        
        databaseFile = createDatabaseFile();

        connection = getConnection();

        getConnection().setAutoCommit(false);
    }

    /**
     * Creates the tables for the provided features.
     *
     * @param featureName the features to create tables for.
     */
    public void createTable(String featureName) {
        if (!enabled) {
            return;
        }

        try (Statement statement = getConnection().createStatement()) {
            String createRawTableSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                    .append(formatFeatureNameToSQL(featureName)).append("_feature_raw")
                    .append("(id INTEGER PRIMARY KEY AUTOINCREMENT, raw_value REAL NOT NULL);")
                    .toString();

            String createPredictedTableSQL = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                    .append(formatFeatureNameToSQL(featureName)).append("_feature_prediction")
                    .append("(id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + " predictor_settings TEXT NOT NULL,"
                            + " original_data TEXT NOT NULL,"
                            + " preprocessed_data TEXT NOT NULL,"
                            + " test_data_beginning TEXT NOT NULL,"
                            + " test_data_end TEXT NOT NULL,"
                            + " prediction_future TEXT NOT NULL,"
                            + " prediction_test TEXT NOT NULL,"
                            + " error_metrics TEXT NOT NULL,"
                            + " prediction_time TEXT NOT NULL,"
                            + " prediction_number INTEGER NOT NULL)")
                    .toString();

            statement.execute(createRawTableSQL);
            statement.execute(createPredictedTableSQL);

            connection.commit();
        } catch (SQLException e) {
            PredictionLogger.error("SQLite table creation failed", "Failed to create table");
        }
    }

    /**
     * Method to add a row to the feature's raw data table.
     *
     * @param featureName the name of the feature.
     * @param data the new data raw for the provided feature.
     */
    public void addRawDataToTable(String featureName, Double data) {
        if (!enabled) {
            return;
        }
        
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

    /**
     * Method to add a row to the feature's prediction data table.
     *
     * @param featureName the name of the feature.
     * @param prediction the new data prediction for the provided feature.
     */
    public void addPredictionDataToTable(String featureName, Prediction prediction) {
        if (!enabled) {
            return;
        }
        
        String insertSQL = new StringBuilder(" INSERT INTO ")
                .append(formatFeatureNameToSQL(featureName))
                .append("_feature_prediction (predictor_settings,original_data,preprocessed_data,"
                        + "test_data_beginning,test_data_end,prediction_future,"
                        + "prediction_test,error_metrics,prediction_time,prediction_number) "
                        + "VALUES ( ? , ? , ? , ? , ? , ? , ? , ? , ? , ? );")
                .toString();
        try (PreparedStatement preparedStatement = getConnection().prepareStatement(insertSQL)) {
            JSONObject predictionJson = JsonParser.toJson(prediction, Prediction.class);

            preparedStatement.setString(1, predictionJson.getJSONObject("prediction_settings").toString());
            preparedStatement.setString(2, predictionJson.getJSONObject("original_data").toString());
            preparedStatement.setString(3, predictionJson.getJSONObject("preprocessed_data").toString());
            preparedStatement.setString(4, predictionJson.getJSONObject("test_data_beginning").toString());
            preparedStatement.setString(5, predictionJson.getJSONObject("test_data_end").toString());
            preparedStatement.setString(6, predictionJson.getJSONObject("prediction_future").toString());
            preparedStatement.setString(7, predictionJson.getJSONObject("prediction_test").toString());
            preparedStatement.setString(8, predictionJson.getJSONObject("error_metrics").toString());
            preparedStatement.setDouble(9, predictionJson.getDouble("prediction_time"));
            preparedStatement.setInt(10, predictionJson.getInt("prediction_number"));

            preparedStatement.execute();
            getConnection().commit();
        } catch (SQLException e) {
            PredictionLogger.error("SQLite Insert error", e.getMessage());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Method for the creation of the database file.
     *
     * @return The created database file.
     * @throws IOException if couldn't create database file.
     */
    private File createDatabaseFile() throws IOException {
        if (!enabled) {
            return null;
        }
        
        PredictionLogger.info("SQLite setup", "Creating database...");

        File databaseFile = new File(
                new StringBuilder(ScenarioBase.resultDirectory).append(File.separator).append("database.db").toString()
        );

        if (!databaseFile.createNewFile()) {
            PredictionLogger.error("SQLite setup failed", "Failed to create database file");
            throw new IOException("Failed to create database file");
        }

        PredictionLogger.info("SQLite setup", "Database created");

        return databaseFile;
    }

    /**
     * Util method for transforming characters which couldn't be used in a table's name.
     *
     * @param name the name to be transformed
     * @return the transformed name
     */
    private static String formatFeatureNameToSQL(String name) {
        return name.replace(":", "__");
    }
}
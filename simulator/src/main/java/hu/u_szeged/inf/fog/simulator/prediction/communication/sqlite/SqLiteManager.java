package hu.u_szeged.inf.fog.simulator.prediction.communication.sqlite;

import hu.u_szeged.inf.fog.simulator.demo.ScenarioBase;
import hu.u_szeged.inf.fog.simulator.prediction.Prediction;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionConfigurator;
import hu.u_szeged.inf.fog.simulator.prediction.PredictionLogger;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.EvaluationMetrics;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleCoordinator;
import hu.u_szeged.inf.fog.simulator.prediction.lifecycle.LifecycleManager;
import hu.u_szeged.inf.fog.simulator.prediction.parser.JsonParser;
import hu.u_szeged.inf.fog.simulator.prediction.settings.PairPredictionSettings;
import hu.u_szeged.inf.fog.simulator.prediction.settings.SimulationSettings;
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

/**
 * Provides SQLite database setup and persistence for prediction and lifecycle
 * data shown by the predictor UI.
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
     * Returns the SQLite connection, creating it when necessary.
     *
     * @return the connection, or null when database output is disabled
     */
    public static Connection getConnection() {
        if (!enabled) {
            return null;
        }

        if (connection == null) {
            String driverString = "jdbc:sqlite:";

            try {
                connection = DriverManager.getConnection(
                        driverString + databaseFile.getAbsolutePath()
                );
            } catch (SQLException e) {
                PredictionLogger.error(
                        "SQLite error",
                        "Failed to get connection"
                );
            }
        }

        return connection;
    }

    /**
     * Returns the database file, creating it when necessary.
     *
     * @return the database file
     * @throws SQLException when database setup fails
     * @throws IOException when file creation fails
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
     * Creates the database file and configures manual commits.
     *
     * @throws IOException when the database file cannot be created
     * @throws SQLException when database setup fails
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
     * Creates all database tables used by one feature.
     *
     * @param featureName the feature name
     */
    public void createTable(String featureName) {
        if (!enabled) {
            return;
        }

        String sqlName = formatFeatureNameToSQL(featureName);

        try (Statement statement = getConnection().createStatement()) {
            String createRawTableSql = "CREATE TABLE IF NOT EXISTS "
                    + sqlName
                    + "_feature_raw "
                    + "(id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "raw_value REAL NOT NULL);";

            String createPredictionTableSql =
                    "CREATE TABLE IF NOT EXISTS "
                    + sqlName
                    + "_feature_prediction "
                    + "(id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "predictor_settings TEXT NOT NULL, "
                    + "original_data TEXT NOT NULL, "
                    + "preprocessed_data TEXT NOT NULL, "
                    + "test_data_beginning TEXT NOT NULL, "
                    + "test_data_end TEXT NOT NULL, "
                    + "prediction_future TEXT NOT NULL, "
                    + "prediction_test TEXT NOT NULL, "
                    + "error_metrics TEXT NOT NULL, "
                    + "prediction_time TEXT NOT NULL, "
                    + "prediction_number INTEGER NOT NULL);";

            String createLifecycleTableSql =
                    "CREATE TABLE IF NOT EXISTS "
                    + sqlName
                    + "_feature_lifecycle "
                    + "(predictor_name TEXT PRIMARY KEY, "
                    + "lifecycle_state TEXT NOT NULL, "
                    + "is_active INTEGER NOT NULL, "
                    + "latest_metrics TEXT, "
                    + "updated_at TEXT NOT NULL);";

            statement.execute(createRawTableSql);
            statement.execute(createPredictionTableSql);
            statement.execute(createLifecycleTableSql);

            connection.commit();
        } catch (SQLException e) {
            PredictionLogger.error(
                    "SQLite table creation failed",
                    "Failed to create table"
            );
        }
    }

    /**
     * Stores one raw feature value.
     *
     * @param featureName the feature name
     * @param data the raw value
     */
    public void addRawDataToTable(String featureName, Double data) {
        if (!enabled) {
            return;
        }

        String insertSql = "INSERT INTO "
                + formatFeatureNameToSQL(featureName)
                + "_feature_raw (raw_value) VALUES (?);";

        try (PreparedStatement preparedStatement =
                getConnection().prepareStatement(insertSql)) {
            preparedStatement.setDouble(1, data);
            preparedStatement.execute();
            getConnection().commit();
        } catch (SQLException e) {
            PredictionLogger.error(
                    "SQLite Insert error",
                    e.getMessage()
            );
        }
    }

    /**
     * Stores one prediction result.
     *
     * @param featureName the feature name
     * @param prediction the prediction result
     */
    public void addPredictionDataToTable(
            String featureName,
            Prediction prediction
    ) {
        if (!enabled) {
            return;
        }

        String insertSql = "INSERT INTO "
                + formatFeatureNameToSQL(featureName)
                + "_feature_prediction "
                + "(predictor_settings, original_data, preprocessed_data, "
                + "test_data_beginning, test_data_end, prediction_future, "
                + "prediction_test, error_metrics, prediction_time, "
                + "prediction_number) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";

        try (PreparedStatement preparedStatement =
                getConnection().prepareStatement(insertSql)) {
            JSONObject predictionJson = JsonParser.toJson(
                    prediction,
                    Prediction.class
            );

            preparedStatement.setString(
                    1,
                    predictionJson.getJSONObject(
                            "prediction_settings"
                    ).toString()
            );
            preparedStatement.setString(
                    2,
                    predictionJson.getJSONObject(
                            "original_data"
                    ).toString()
            );
            preparedStatement.setString(
                    3,
                    predictionJson.getJSONObject(
                            "preprocessed_data"
                    ).toString()
            );
            preparedStatement.setString(
                    4,
                    predictionJson.getJSONObject(
                            "test_data_beginning"
                    ).toString()
            );
            preparedStatement.setString(
                    5,
                    predictionJson.getJSONObject(
                            "test_data_end"
                    ).toString()
            );
            preparedStatement.setString(
                    6,
                    predictionJson.getJSONObject(
                            "prediction_future"
                    ).toString()
            );
            preparedStatement.setString(
                    7,
                    predictionJson.getJSONObject(
                            "prediction_test"
                    ).toString()
            );
            preparedStatement.setString(
                    8,
                    predictionJson.getJSONObject(
                            "error_metrics"
                    ).toString()
            );
            preparedStatement.setDouble(
                    9,
                    predictionJson.getDouble("prediction_time")
            );
            preparedStatement.setInt(
                    10,
                    predictionJson.getInt("prediction_number")
            );

            preparedStatement.execute();
            getConnection().commit();
        } catch (SQLException e) {
            PredictionLogger.error(
                    "SQLite Insert error",
                    e.getMessage()
            );
        } catch (JSONException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Persists the newest lifecycle state for every configured predictor of
     * one feature. It is called after each real value has been evaluated.
     *
     * @param featureName the feature whose lifecycle state is synchronized
     */
    public void syncLifecycleData(String featureName) {
        if (!enabled || SimulationSettings.get() == null
                || SimulationSettings.get().getPredictionSettings() == null) {
            return;
        }

        String upsertSql = "INSERT INTO "
                + formatFeatureNameToSQL(featureName)
                + "_feature_lifecycle "
                + "(predictor_name, lifecycle_state, is_active, "
                + "latest_metrics, updated_at) "
                + "VALUES (?, ?, ?, ?, datetime('now')) "
                + "ON CONFLICT(predictor_name) DO UPDATE SET "
                + "lifecycle_state = excluded.lifecycle_state, "
                + "is_active = excluded.is_active, "
                + "latest_metrics = excluded.latest_metrics, "
                + "updated_at = excluded.updated_at;";

        try (PreparedStatement preparedStatement =
                getConnection().prepareStatement(upsertSql)) {
            LifecycleManager lifecycleManager =
                    LifecycleManager.getInstance();

            for (PairPredictionSettings predictionSettings
                    : SimulationSettings.get().getPredictionSettings()) {
                String predictorName =
                        predictionSettings.getPredictorName();

                LifecycleCoordinator coordinator =
                        lifecycleManager.getCoordinator(
                                predictorName,
                                featureName
                        );

                if (coordinator == null) {
                    continue;
                }

                preparedStatement.setString(1, predictorName);
                preparedStatement.setString(
                        2,
                        coordinator.getState().name()
                );
                preparedStatement.setInt(
                        3,
                        lifecycleManager.isActive(
                                predictorName,
                                featureName
                        ) ? 1 : 0
                );
                preparedStatement.setString(
                        4,
                        createMetricsJson(
                                coordinator.getLatestMetrics()
                        )
                );

                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();
            getConnection().commit();
        } catch (SQLException e) {
            PredictionLogger.error(
                    "SQLite lifecycle synchronization error",
                    e.getMessage()
            );
        }
    }

    /**
     * Converts optional evaluation metrics to JSON text for Electron.
     *
     * @param metrics the latest metrics, or null before evaluation
     * @return JSON text, or null when metrics are unavailable
     */
    private String createMetricsJson(EvaluationMetrics metrics) {
        if (metrics == null) {
            return null;
        }

        return "{"
                + "\"matched_samples\":"
                + metrics.getMatchedSamples()
                + ",\"accuracy\":"
                + metrics.getAccuracy()
                + ",\"mae\":"
                + metrics.getMae()
                + ",\"mse\":"
                + metrics.getMse()
                + ",\"rmse\":"
                + metrics.getRmse()
                + ",\"smape\":"
                + metrics.getSmape()
                + "}";
    }

    /**
     * Creates the database file in the current simulation result directory.
     *
     * @return the new database file
     * @throws IOException when file creation fails
     */
    private File createDatabaseFile() throws IOException {
        if (!enabled) {
            return null;
        }

        PredictionLogger.info("SQLite setup", "Creating database...");

        File result = new File(
                ScenarioBase.resultDirectory
                        + File.separator
                        + "database.db"
        );

        if (!result.createNewFile()) {
            PredictionLogger.error(
                    "SQLite setup failed",
                    "Failed to create database file"
            );
            throw new IOException("Failed to create database file");
        }

        PredictionLogger.info("SQLite setup", "Database created");

        return result;
    }

    /**
     * Converts feature names to safe SQLite table prefixes.
     *
     * @param name the feature name
     * @return the safe SQLite table prefix
     */
    private static String formatFeatureNameToSQL(String name) {
        return name.replace(":", "__");
    }
}
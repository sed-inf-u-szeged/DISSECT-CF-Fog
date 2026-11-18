package hu.u_szeged.inf.fog.simulator.prediction;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OnlineModelSelectionTest {
    private OnlineModelLifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = new OnlineModelLifecycleManager(OnlineModelCatalog.builtIn());
    }

    @Test
    void defaultIncludesEveryRegisteredModelWithoutASelectionArgument() {
        assertEquals(List.of("LSTM", "TCN", "GRU", "PATCHTST"), manager.getModelTypes());
        assertNotSame(manager.getControllers().get(0), manager.getControllers().get(1));
        assertEquals(4, manager.getControllers().stream().distinct().count());
        assertTrue(manager.isManagedModelType("gru"));
        assertTrue(manager.isManagedModelType("patchtst"));
        assertThrows(UnsupportedOperationException.class, () -> manager.getControllers().clear());
    }

    @Test
    void anAdditionalAdapterIsAutomaticallyScheduledByTheDefault() {
        // A stub identity exercises extensibility beyond the three installed models.
        OnlineModelAdapter extra = new LstmModelAdapter() {
            @Override public String getModelType() { return "EXTRA"; }
            @Override public String getVersionName(int version) { return "Extra256-v" + version; }
        };
        OnlineModelLifecycleManager extended = new OnlineModelLifecycleManager(
                new OnlineModelCatalog(List.of(new LstmModelAdapter(), new TcnModelAdapter(),
                        new GruModelAdapter(), extra)));
        assertEquals(List.of("LSTM", "TCN", "GRU", "EXTRA"), extended.getModelTypes());
        assertTrue(extended.isManagedModelType("extra"));
        assertFalse(extended.isManagedModelType("ARIMA"));
        assertFalse(extended.isManagedModelType("LINEAR_REGRESSION"));
        assertEquals(4, extended.getControllers().stream().distinct().count());
    }

    @Test
    void explicitSelectionsRemainOptionalFilters() {
        manager.configure("tcn");
        assertEquals(List.of("TCN"), manager.getModelTypes());
        manager.configure("LSTM");
        assertEquals(List.of("LSTM"), manager.getModelTypes());
        manager.configure(" tcn , lstm ");
        assertEquals(List.of("LSTM", "TCN"), manager.getModelTypes());
        manager.configure("gru");
        assertEquals(List.of("GRU"), manager.getModelTypes());
        manager.configure("gru,tcn,lstm");
        assertEquals(List.of("LSTM", "TCN", "GRU"), manager.getModelTypes());
        manager.configure("patchtst");
        assertEquals(List.of("PATCHTST"), manager.getModelTypes());
        manager.configure(null);
        assertEquals(List.of("LSTM", "TCN", "GRU", "PATCHTST"), manager.getModelTypes());
    }

    @Test
    void olderBothCommandStillWorks() {
        manager.configure("BOTH");
        assertEquals(List.of("LSTM", "TCN"), manager.getModelTypes());
    }

    @Test
    void invalidOrDuplicateSelectionsDoNotReplaceTheRunningConfiguration() {
        var before = manager.getControllers();
        for (String invalid : List.of("UNKNOWN", "", "TCN,", "LSTM,LSTM", "GRU,GRU", "ALL,TCN")) {
            assertThrows(IllegalArgumentException.class, () -> manager.configure(invalid));
            assertSame(before, manager.getControllers());
        }
    }

    @Test
    void anyControllerStartingDataCollectionLocksTheSelection() throws Exception {
        var before = manager.getControllers();
        Field window = LstmOnlineLifecycleController.class.getDeclaredField("trainingWindowPoints");
        window.setAccessible(true);
        window.setInt(before.get(1), 287);
        assertThrows(IllegalStateException.class, () -> manager.configure("LSTM"));
        assertSame(before, manager.getControllers());
    }

    @Test
    void aStartedVersionAlsoLocksTheSelection() throws Exception {
        var before = manager.getControllers();
        Field version = LstmOnlineLifecycleController.class.getDeclaredField("versionNumber");
        version.setAccessible(true);
        version.setInt(before.get(0), 1);
        assertThrows(IllegalStateException.class, () -> manager.configure("TCN"));
        assertSame(before, manager.getControllers());
    }

    @Test
    void invalidCatalogsFailBeforeSimulationStarts() {
        assertThrows(IllegalArgumentException.class, () -> new OnlineModelCatalog(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new OnlineModelCatalog(
                List.of(new LstmModelAdapter(), new LstmModelAdapter())));
        OnlineModelAdapter unsafe = new LstmModelAdapter() {
            @Override public String getModelType() { return "../GRU"; }
        };
        assertThrows(IllegalArgumentException.class, () -> new OnlineModelCatalog(List.of(unsafe)));
        OnlineModelAdapter collision = new LstmModelAdapter() {
            @Override public String getModelType() { return "GRU"; }
        };
        assertThrows(IllegalArgumentException.class, () -> new OnlineModelCatalog(
                List.of(new LstmModelAdapter(), collision)));
    }
}

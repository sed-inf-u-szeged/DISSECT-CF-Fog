package hu.u_szeged.inf.fog.simulator.test.random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import hu.mta.sztaki.lpds.cloud.simulator.util.SeedSyncer;

public class SimRandomTest {

    @Test
    void modifySeed() {
        
        final int size = 5;
        int[] array1 = new int[size];  
        int[] array2 = new int[size];
                
        SeedSyncer.resetCentral();
        
        for(int i=0;i<size;i++) {
            array1[i] = SeedSyncer.centralRnd.nextInt()%10;
            System.out.println(array1[i]);
        }
        
        SeedSyncer.resetCentral();
        
        for(int i=0;i<size;i++) {
            array2[i] = SeedSyncer.centralRnd.nextInt()%10;
            System.out.println(array2[i]);
        }
        
        assertArrayEquals(array1, array2);
        
        SeedSyncer.modifySeed(System.currentTimeMillis());
        
        for(int i=0;i<size;i++) {
            array2[i] = SeedSyncer.centralRnd.nextInt()%10;
            System.out.println(array2[i]);
        }
        
        assertFalse(Arrays.equals(array1, array2));
    }
    
}

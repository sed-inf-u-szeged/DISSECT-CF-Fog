package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;

public class Task extends StorageObject {

    private static int count = 1;

    // ez még kérdéses milyen érték és a prio minél kisebb annál fontosabb vagy fordítva,
    // fix intervallum legyen vagy dinamikusabb (összes taskot be lehetne sorolni vagy csak egy általános)
    public int priority;

    //a tick ameddig kész kell lenni
    public long deadline;

    public Task(String myid, long mysize, boolean vary) {
        super(myid, mysize, vary);
    }

    public Task(long size, int priority, long deadline) {
        super("Task" + count++, size, false);
        this.priority = priority;
        this.deadline = deadline;
    }
}

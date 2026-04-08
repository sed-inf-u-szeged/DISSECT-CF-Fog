package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import lombok.Getter;

@Getter
public class Task extends StorageObject {

    //a taskok elnevezéséhez számláló, ha tipusosak lesznek lehet le lesz cserélve
    private static int count = 1;

    // 1-10
    // 1 - lowest, 10 - highest
    public int priority;

    //az idő amikor létre lett hozva, nem tudom lesz e használva, de elfér
    public long created;

    //a tick ameddig kész kell lenni - egyelőre 10p = 600000 tick minden
    public long deadline;

    public Task(String myid, long mysize, boolean vary) {
        super(myid, mysize, vary);
    }

    public Task(long size, int priority, long deadline) {
        super("Task" + count++, size, false);
        this.priority = priority;
        this.deadline = deadline;
        this.created = Timed.getFireCount();
    }
}

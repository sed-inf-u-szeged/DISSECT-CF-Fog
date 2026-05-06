package hu.u_szeged.inf.fog.simulator.iot;

import hu.mta.sztaki.lpds.cloud.simulator.Timed;
import hu.mta.sztaki.lpds.cloud.simulator.io.StorageObject;
import lombok.Getter;

import java.util.*;

@Getter
public class Task extends StorageObject {

    //a taskok elnevezéséhez számláló, ha tipusosak lesznek lehet le lesz cserélve
    private static int count = 1;

    //ilyen típusú taskok, ilyen típusú app dolgozhat fel
    public TaskType type;

    // 1-10
    // 1 - lowest, 10 - highest
    public int priority;

    //az idő amikor létre lett hozva, nem tudom lesz e használva, de elfér
    public long created;

    //a tick ameddig kész kell lenni - egyelőre 10p = 600000 tick minden
    public long deadline;

    //azon eszközök listája akiket "értesíteni kell" -> vszeg egy log lesz amiben meg lesznek említve mert visszacsatolás fájdalmas
    public Set<Device> notify = new HashSet<>();

    //alapból kis taskok ezzel jönnek létre
    public Task(long size, int priority, long deadline, TaskType type, Device toNotify) {
        super("Task" + count++ +"-type:"+type, size, false);
        this.priority = priority;
        this.deadline = deadline;
        this.type = type;
        notify.add(toNotify);
        this.created = Timed.getFireCount();
    }

    //task mergelés a más féle név miatt másik konstruktor
    private Task(String id, long size, int priority, long deadline, TaskType type, List<Device> toNotify) {
        super(id, size, false);
        this.priority = priority;
        this.deadline = deadline;
        this.type = type;
        this.notify.addAll(toNotify);
        this.created = Timed.getFireCount();
    }

    //összevon x taskot(az hogy mennyi mekkora taskot von össze az az appban lesz)
    //a prio és deadline mindig a legszigorúbb, de általában úgyis hasonló prio és deadlineú taskok lesznek mergelve
    public static Task merge(Collection<StorageObject> tasks, TaskType type) {
        StringBuilder sb = new StringBuilder();
        sb.append(type).append("-MergedTask");

        long combinedSize = 0;
        long closestDeadline = Long.MAX_VALUE;
        int highestPriority = 0;
        List<Device> mergedNotify = new ArrayList<>();

        for (StorageObject task : tasks) {
            if(!(task instanceof Task) || ((Task) task).type != type) {
                continue;
            }

            if(highestPriority < ((Task) task).priority){
                highestPriority = ((Task) task).priority;
            }
            if(closestDeadline > ((Task) task).deadline){
                closestDeadline = ((Task) task).deadline;
            }
            combinedSize += task.size;
            mergedNotify.addAll(((Task) task).notify);

            //id összerakás, így rendkivül hosszú idk lehetnek ha 5+ task mergelődik, lehet lehetne okosabban, de nem tom számít-e
            int start = task.id.indexOf("Task") + 4;
            int end = task.id.indexOf("-");
            if(end == -1){
                end = task.id.lastIndexOf("+")+2;
            }
            sb.append(task.id, start, end);
            sb.append("+");
        }
        sb.deleteCharAt(sb.length() - 1);
        return new Task(sb.toString(),combinedSize, highestPriority, closestDeadline, type, mergedNotify);
    }

    //frissiti a beadott collection taskjait az alapján hogy a deadline mennyire közeleg a létrehozatalhoz képest
    public static Collection<StorageObject> update(Collection<StorageObject> tasks) {
        //TODO prio max 10; deadline - jelenlegi tick / 10 és ez lesz 1 arány ami mindig +1 prio (tehát medical a 6os prioval 4 ilyen "arány" után lesz maxos)

        //ha ezt meghivom az appban lévő setre, akkor nem lesz rendezve a modosítás után
        //opt1 reinsert (mindent kiveszek és visszarkok, eléggé overkill vszeg)
        //opt2 resort after update
        return tasks;
    }


    @Override
    public String toString() {
        return "SO(id:" + id + ", size:" + size + ", priority:" + priority + ", deadline:" + deadline + ")";
    }
}

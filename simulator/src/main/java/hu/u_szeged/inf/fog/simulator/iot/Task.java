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
    public Set<String> notify = new HashSet<>();

    //alapból kis taskok ezzel jönnek létre
    public Task(long size, int priority, long deadline, TaskType type, Device toNotify) {
        super("Task" + count++ +"-type:"+type, size, false);
        this.priority = priority;
        this.deadline = deadline;
        this.type = type;
        notify.add("Device-" + toNotify.hashCode());
        this.created = Timed.getFireCount();
    }

    //task mergeléshez a más féle név miatt másik konstruktor
    private Task(String id, long size, int priority, long deadline, TaskType type, Set<String> toNotify) {
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
        Set<String> mergedNotify = new HashSet<>();

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

    //frissiti a beadott collection taskjainak prioritását az alapján hogy a deadline mennyire közeleg a létrehozatalhoz képest
    public static Set<Task> update(Set<Task> tasks) {
        //prio max TaskType alapján prio + 4 legfeljebb (medical max 10, traffic max 7, weather max 5);
        Set<Task> updated = new TreeSet<>(
                Comparator.comparing(Task::getPriority)
                        .reversed()
                        .thenComparing(Task::getDeadline)
        );

        for (Task t : tasks) {
            // új task létrehozása, hogy rendezett set legyen a módosítások után is
            long currentTime = Timed.getFireCount();
            int newPriority;
            double ratio = (double) Math.max(t.deadline - currentTime, 0) / (t.deadline - t.created);
            if(ratio<0.2){
                newPriority = t.type.getPriority()+4;
            } else if (ratio<0.4) {
                newPriority = t.type.getPriority()+3;
            } else if (ratio<0.6) {
                newPriority = t.type.getPriority()+2;
            } else if (ratio<0.8) {
                newPriority = t.type.getPriority()+1;
            } else{
                newPriority = t.type.getPriority();
            }
            Task newTask = new Task(t.id,t.size,newPriority,t.deadline,t.type,t.notify);
            updated.add(newTask);
        }

        return updated;
    }


    @Override
    public String toString() {
        return "SO(id:" + id + ", size:" + size + ", priority:" + priority + ", deadline:" + deadline + ")";
    }
}

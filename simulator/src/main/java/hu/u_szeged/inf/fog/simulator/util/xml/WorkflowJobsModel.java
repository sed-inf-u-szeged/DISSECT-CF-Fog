package hu.u_szeged.inf.fog.simulator.util.xml;

import java.util.ArrayList;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "adag")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class WorkflowJobsModel {

    ArrayList<WorkflowJobModel> jobList;
    String name;
    int repeat;
    String type;

    public ArrayList<WorkflowJobModel> getJobs() {
        return jobList;
    }

    @XmlElement(name = "job")
    public void setJobs(ArrayList<WorkflowJobModel> jobs) {
        this.jobList = jobs;
    }

    @XmlAttribute(name = "name")
    public void setName(String name) {
        this.name = name;
    }

    @XmlAttribute(name = "type")
    public void setType(String type) {
        this.type = type;
    }

    @XmlAttribute(name = "repeat")
    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    @Override
    public String toString() {
        StringBuffer str = new StringBuffer(); // TODO: replace to StringBuilder?
        for (WorkflowJobModel job : this.jobList) {
            str.append(job.toString());
            str.append("\n");
        }
        return str.toString();
    }

}
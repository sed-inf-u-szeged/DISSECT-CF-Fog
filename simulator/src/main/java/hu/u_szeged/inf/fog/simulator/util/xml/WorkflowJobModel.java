package hu.u_szeged.inf.fog.simulator.util.xml;

import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob;
import hu.u_szeged.inf.fog.simulator.workflow.WorkflowJob.Uses;
import java.io.File;
import java.util.ArrayList;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang3.tuple.Pair;

@XmlRootElement(name = "job")
@XmlAccessorType(XmlAccessType.PROPERTY)
public class WorkflowJobModel {

    public String id;
    public ArrayList<UsesXmlModel> uses;
    public double runtime;
    public double longitude;
    public double latitude;

    @XmlAttribute(name = "id", required = true)
    public void setId(String id) {
        this.id = id;
    }

    @XmlAttribute(name = "runtime")
    public void setRuntime(double runtime) {
        this.runtime = runtime;
    }

    @XmlAttribute(name = "long")
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    @XmlAttribute(name = "lat")
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    @XmlElement(name = "uses")
    public void setUses(ArrayList<UsesXmlModel> uses) {
        this.uses = uses;
    }

    public void add(UsesXmlModel device) {
        if (this.uses == null) {
            this.uses = new ArrayList<UsesXmlModel>();
        }
        this.uses.add(device);
    }

    public ArrayList<UsesXmlModel> getUses() {
        return uses;
    }

    @Override
    public String toString() {
        return "WorkflowJobModel [id=" + id + ", uses=" + uses + ", runtime=" + runtime + ", longitude=" + longitude
                + ", latitude=" + latitude + "]";
    }

    public static Pair<String, ArrayList<WorkflowJob>> loadWorkflowXml(String workflowfile, String name) throws JAXBException {
        
        File file = new File(workflowfile);
        JAXBContext jaxbContext = JAXBContext.newInstance(WorkflowJobsModel.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        WorkflowJobsModel job = (WorkflowJobsModel) jaxbUnmarshaller.unmarshal(file);

        String appName = job.name + name;
        ArrayList<WorkflowJob> jobs = new ArrayList<WorkflowJob>();
        System.out.print("Loading " + appName + " workflow..");
        int jobCount = 0;
        for (int repeatIndex = 0; repeatIndex < job.repeat; repeatIndex++) {

            for (int jobIndex = 0; jobIndex < job.jobList.size(); jobIndex++) {
                WorkflowJobModel wjModel = job.jobList.get(jobIndex);

                ArrayList<Uses> inputs = new ArrayList<>();
                ArrayList<Uses> outputs = new ArrayList<>();

                for (int usesIndex = 0; usesIndex < wjModel.uses.size(); usesIndex++) {

                    UsesXmlModel usesModel = wjModel.uses.get(usesIndex);
                    if (usesModel.link.equals("input")) {
                        inputs.add(new Uses(Uses.Type.valueOf(usesModel.type.toUpperCase()), usesModel.size,
                                usesModel.runtime, usesModel.activate, usesModel.amount, usesModel.id));
                    } else {
                        outputs.add(new Uses(Uses.Type.valueOf(usesModel.type.toUpperCase()), usesModel.size,
                                usesModel.runtime, usesModel.activate, usesModel.amount,
                                repeatIndex + "_" + usesModel.id));
                    }
                }
                WorkflowJob wj = new WorkflowJob(repeatIndex + "_" + wjModel.id, wjModel.runtime, wjModel.longitude, wjModel.latitude,
                        WorkflowJob.State.SUBMITTED, inputs, outputs);
                jobCount++;
                jobs.add(wj);
            }
        }
        System.out.println(jobCount + " jobs loaded.");
        return Pair.of(appName, jobs);
    }
    


}
package hu.u_szeged.inf.fog.simulator.provider;

import java.util.ArrayList;
import java.util.Arrays;

import hu.u_szeged.inf.fog.simulator.application.Application;

// https://www.ibm.com/cloud/blog/better-pricing-better-standard-plan-watson-iot-platform
// https://cloud.google.com/iot/pricing
public class IBMProvider extends Provider {
	
	public static class ExchangeInterval {

		double mbto;
		double mbfrom;
		double cost;

		public ExchangeInterval(double mbfrom, double mbto, double cost) {
			this.mbto = mbto;
			this.mbfrom = mbfrom;
			this.cost = cost;
		}
	}

	static ArrayList<ExchangeInterval> exchangeIntervals = new ArrayList<>();

	final static ArrayList<ExchangeInterval> defaultexchangeIntervals = new ArrayList<>(
			Arrays.asList(
					new ExchangeInterval(0, 449_999, 0.001), 
					new ExchangeInterval(450_000, 6_999_999, 0.0007),
					new ExchangeInterval(7_000_000, Double.MAX_VALUE, 0.00014)));

	public IBMProvider() {
		this.name = "IBM";
		Provider.providers.add(this);
	}
	
	public IBMProvider(ArrayList<ExchangeInterval> exchangeIntervals) {
		this.name = "IBM";
		IBMProvider.exchangeIntervals = exchangeIntervals;
		Provider.providers.add(this);
	}
	
	@Override
	public double calculate() {

		double totalprocessedSizeInMB = (double) Application.totalProcessedSize / 1048576; // 1 MB
		double cost = 0.0;

		ArrayList<ExchangeInterval> intervals = exchangeIntervals.isEmpty() ? defaultexchangeIntervals
				: IBMProvider.exchangeIntervals;
		
		for (ExchangeInterval ei : intervals) {
			if (totalprocessedSizeInMB <= ei.mbto && totalprocessedSizeInMB >= ei.mbfrom) {
				cost = ei.cost;
			}
		}

		this.cost = totalprocessedSizeInMB * cost;
		return this.cost;
	}
}

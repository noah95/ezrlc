package ezrlc.Model;

import java.io.File;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

import ezrlc.Model.RectPlotNewMeasurement.Unit;
import ezrlc.ModelCalculation.MCEqCircuit;
import ezrlc.ModelCalculation.MCOptions;
import ezrlc.ModelCalculation.MCUtil;
import ezrlc.ModelCalculation.MCWorker;
import ezrlc.ModelCalculation.MCWorker.WorkerMode;
import ezrlc.RFData.RFData;
import ezrlc.RFData.RFData.MeasurementType;
import ezrlc.util.Complex;
import ezrlc.util.DataSource;
import ezrlc.util.MathUtil;

/**
 * Implements top level model functionality of the MVC framework
 * 
 * @author noah
 *
 */
public class Model extends Observable {

	// ================================================================================
	// Public Data
	// ================================================================================
	public enum UpdateEvent {
		MANUAL, FILE, NEW_EQC, REMOVE_EQC, CHANGE_EQC
	};

	// ================================================================================
	// Private Data
	// ================================================================================
	private RFData rfDataFile;

	private List<DataSet> dataSets = new ArrayList<DataSet>();

	private List<MCEqCircuit> eqCircuits = new ArrayList<MCEqCircuit>();

	// Stores the plotDataSetList ids to the corresponding model id
	private List<Integer[]> eqCircuitRectPlotIDs = new ArrayList<Integer[]>();
	private List<Integer[]> eqCircuitSmithPlotIDs = new ArrayList<Integer[]>();

	MCWorker worker;

	public Model() {
	}

	// ================================================================================
	// Private Functions
	// ================================================================================
	/**
	 * Builds a new dataset with the given settings
	 * 
	 * @param nm
	 *            RectPlotNewMeasurement
	 * @return DataSet
	 */
	private DataSet buildDataSetRaw(RectPlotNewMeasurement nm) {
		Complex[] data = null;
		Complex[] datacomp = null;
		double[] outdata = null;
		// Get Data

		if (nm.src == DataSource.FILE) {
			outdata = new double[rfDataFile.size()];
			switch (nm.type) {
			case S:
				data = this.rfDataFile.getSData(nm.zRef);
				break;
			case Z:
				data = this.rfDataFile.getzData();
				break;
			case Y:
				data = this.rfDataFile.getyData();
				break;
			case Rs:
				outdata = RFData.z2Rs(this.rfDataFile.getzData());
				break;
			case Rp:
				outdata = RFData.y2Rp(this.rfDataFile.getyData());
				break;
			case Ls:
				outdata = RFData.z2Ls(this.rfDataFile.getzData(), rfDataFile.getfData());
				break;
			case Lp:
				outdata = RFData.y2Lp(this.rfDataFile.getyData(), rfDataFile.getfData());
				break;
			case Cs:
				outdata = RFData.z2Cs(this.rfDataFile.getzData(), rfDataFile.getfData());
				break;
			case Cp:
				outdata = RFData.y2Cp(this.rfDataFile.getyData(), rfDataFile.getfData());
				break;
			default:
				break;
			}
		} else if (nm.src == DataSource.MODEL) {
			outdata = new double[eqCircuits.get(nm.eqCircuitID).getWSize()];
			switch (nm.type) {
			case S:
				data = eqCircuits.get(nm.eqCircuitID).getS(nm.zRef);
				break;
			case Y:
				data = eqCircuits.get(nm.eqCircuitID).getY();
				break;
			case Z:
				data = eqCircuits.get(nm.eqCircuitID).getZ();
				break;
			default:
				break;
			}
		} else if (nm.src == DataSource.COMPARE) {
			outdata = new double[eqCircuits.get(nm.eqCircuitID).getWSize()];
			switch (nm.type) {
			case S:
				data = eqCircuits.get(nm.eqCircuitID).getS(nm.zRef);
				datacomp = this.rfDataFile.getSData(nm.zRef);
				break;
			case Y:
				data = eqCircuits.get(nm.eqCircuitID).getY();
				datacomp = this.rfDataFile.getyData();
				break;
			case Z:
				data = eqCircuits.get(nm.eqCircuitID).getZ();
				datacomp = this.rfDataFile.getzData();
				break;
			default:
				break;
			}
		}
		MathUtil.sanitize(outdata);

		// Convert to Complex Modifier
		if (nm.type == MeasurementType.S || nm.type == MeasurementType.Y || nm.type == MeasurementType.Z) {
			outdata = new double[data.length];
			switch (nm.cpxMod) {
			case REAL:
				// Extract real part
				for (int i = 0; i < data.length; i++) {
					outdata[i] = data[i].re();
				}
				break;
			case IMAG:
				// Extract imaginary part
				for (int i = 0; i < data.length; i++) {
					outdata[i] = data[i].im();
				}
				break;
			case MAG:
				// Extract magnitude
				for (int i = 0; i < data.length; i++) {
					outdata[i] = data[i].abs();
				}
				break;
			case ANGLE:
				// Extract angle
				for (int i = 0; i < data.length; i++) {
					outdata[i] = data[i].angle();
				}
				break;
			default:
				break;
			}
		}

		// If compare
		if (nm.src == DataSource.COMPARE) {
			double[] outdatacompare = new double[datacomp.length];
			switch (nm.cpxMod) {
			case REAL:
				// Extract real part
				for (int i = 0; i < datacomp.length; i++) {
					outdatacompare[i] = datacomp[i].re();
				}
				break;
			case IMAG:
				// Extract imaginary part
				for (int i = 0; i < datacomp.length; i++) {
					outdatacompare[i] = datacomp[i].im();
				}
				break;
			case MAG:
				// Extract magnitude
				for (int i = 0; i < datacomp.length; i++) {
					outdatacompare[i] = datacomp[i].abs();
				}
				break;
			case ANGLE:
				// Extract angle
				for (int i = 0; i < datacomp.length; i++) {
					outdatacompare[i] = datacomp[i].angle();
				}
				break;
			default:
				break;
			}
			// get larger data size
			// outdata: model
			// outdatacompare: File
			int size = outdata.length;
			int filestartidx = MCUtil.getFirstFIndex(eqCircuits.get(nm.eqCircuitID).getOps(), rfDataFile.getfData());
			double[] diff = new double[size];
			for (int i = 0; i < size; i++) {
				diff[i] = Math.abs(outdata[i] - outdatacompare[i + filestartidx]);
			}
			// convert to db
			if (nm.unit == Unit.dB) {
				for (int i = 0; i < size; i++) {
					diff[i] = 20 * Math.log10(diff[i] + Double.MIN_VALUE);
				}
			}
			outdata = diff;
		}

		double[] xdata = null;
		if (nm.src == DataSource.FILE)
			xdata = rfDataFile.getfData();
		if (nm.src == DataSource.MODEL || nm.src == DataSource.COMPARE)
			xdata = eqCircuits.get(nm.eqCircuitID).getF();

		// Now create the dataset and add it to the dataset list
		DataSet dataSet = new DataSet(xdata, outdata, nm);
		return dataSet;
	}

	/**
	 * Create a new dataset and store it in the dataset list
	 * 
	 * @param nm
	 *            RectPlotNewMeasurement object with options
	 * @return id to access the dataset
	 */
	private int buildDataSet(RectPlotNewMeasurement nm) {
		DataSet dataSet = buildDataSetRaw(nm);
		this.dataSets.add(dataSet);

		// Return the number in the list
		int id = this.dataSets.size() - 1;

		// save id in list to associate it to its source
		if (nm.src == DataSource.MODEL) {
			Integer[] lst = eqCircuitRectPlotIDs.get(nm.eqCircuitID);
			Integer[] newlst = new Integer[lst.length + 1];
			System.arraycopy(lst, 0, newlst, 0, lst.length);
			newlst[lst.length] = id;
			eqCircuitRectPlotIDs.set(nm.eqCircuitID, newlst);
		}

		return id;
	}

	/**
	 * Builds a new dataset with the given settings for smith chart use
	 * 
	 * @param nm
	 *            SmithChartNewMeasurement
	 * @return DataSet
	 */
	private DataSet buildSmithChartDataSetRaw(SmithChartNewMeasurement nm) {
		Complex[] data = null;
		DataSet set = null;
		// Get Data
		if (nm.src == DataSource.FILE) {
			data = rfDataFile.getzData();
			set = new DataSet(rfDataFile.getfData(), data, nm);
		} else if (nm.src == DataSource.MODEL) {
			data = eqCircuits.get(nm.eqCircuitID).getZ();
			set = new DataSet(eqCircuits.get(nm.eqCircuitID).getF(), data, nm);
		}
		return set;
	}

	/**
	 * Builds a new Smithchart dataset and returns its unique ID
	 * 
	 * @param nm
	 *            SmithChartNewMeasurement
	 * @return ID in the list of smith chart datasets
	 */
	private int buildSmithChartDataSet(SmithChartNewMeasurement nm) {
		DataSet set = buildSmithChartDataSetRaw(nm);

		// Create set
		this.dataSets.add(set);

		int id = this.dataSets.size() - 1;

		if (nm.src == DataSource.MODEL) {
			// save id in list to associate it to its source
			Integer[] lst = eqCircuitSmithPlotIDs.get(nm.eqCircuitID);
			Integer[] newlst = new Integer[lst.length + 1];
			System.arraycopy(lst, 0, newlst, 0, lst.length);
			newlst[lst.length] = id;
			eqCircuitSmithPlotIDs.set(nm.eqCircuitID, newlst);
		}

		return id;
	}

	// ================================================================================
	// Public Functions
	// ================================================================================
	/**
	 * Parses the given Inputfile and adds a new RFData object
	 * 
	 * @param file
	 *            file
	 */
	public void newInputFile(File file) {
		try {
			rfDataFile = new RFData(file);
			rfDataFile.parse();
		} catch (Exception e) {
			System.err.println("FATAL: Error in file parsing");
		}
	}

	/**
	 * Adds a new Dataset in the model
	 * 
	 * @param nm
	 *            RectPlotNewMeasurement
	 * @return unique data identifier of the plotdataset
	 */
	public int createDataset(RectPlotNewMeasurement nm) {
		return this.buildDataSet(nm);
	}

	/**
	 * Adds a new dataset in the model to be used in a smithchart
	 * 
	 * @param nm
	 *            SmithChartNewMeasurement
	 * @return unique data identifier of the dataset
	 */
	public int createDataset(SmithChartNewMeasurement nm) {
		return this.buildSmithChartDataSet(nm);
	}

	/**
	 * Triggers a notify to the observers
	 */
	public void manualNotify() {
		// mark as value changed
		setChanged();
		notifyObservers(UpdateEvent.MANUAL);
	}

	/**
	 * Get a dataset by its ID
	 * 
	 * @param id
	 *            plot id
	 * @return plot dataset
	 */
	public DataSet getDataSet(int id) {
		return this.dataSets.get(id);
	}

	/**
	 * Get filename of the currently loaded file
	 * 
	 * @return filename
	 */
	public String getFilename() {
		if (this.rfDataFile != null) {
			return this.rfDataFile.getFileName();
		} else
			return null;
	}

	/**
	 * Checks if the given id is a dataset
	 * 
	 * @param id
	 *            plot data set id
	 * @return true, if id exists, false if not
	 */
	public Boolean isDataset(int id) {
		try {
			if (dataSets.get(id) != null)
				return true;
			else
				return false;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Removes a dataset by ID
	 * 
	 * @param id
	 *            id
	 */
	public void removeDataset(int id) {
		dataSets.set(id, null);
		this.setChanged();
		this.notifyObservers();
	}

	/**
	 * Creates a new equivalent circuit based on the given options
	 * 
	 * @param ops
	 *            ops
	 */
	public void createEqCircuit(MCOptions ops) {
		// Create worker, set data and start it
		worker = new MCWorker(this, "MCWorker-1");
		worker.setMCOptions(ops);
		worker.start();
	}

	/**
	 * Returns the ID of the last created Model
	 * 
	 * @return id
	 */
	public int getEQCID() {
		return eqCircuits.size() - 1;
	}

	/**
	 * Returns the current state of the worker
	 * 
	 * @return status
	 */
	public State getWorkerStatus() {
		return worker.getState();
	}

	/**
	 * Returns the calculation worker
	 * 
	 * @return worker
	 */
	public MCWorker getWorker() {
		return worker;
	}

	/**
	 * Gets called if the MCWorker succeeds
	 * 
	 * @param eqc
	 *            Equivalent circuit that was generated
	 * @param mode
	 *            mode
	 */
	public void mcWorkerSuccess(MCEqCircuit eqc, MCWorker.WorkerMode mode) {
		if (mode == WorkerMode.NORMAL) {
			eqCircuits.add(eqc);
			eqCircuitRectPlotIDs.add(new Integer[] {});
			eqCircuitSmithPlotIDs.add(new Integer[] {});
			setChanged();
			notifyObservers(UpdateEvent.NEW_EQC);
		} else if (mode == WorkerMode.OPT_ONLY) {
			// update plots
			for (int i = 0; i < dataSets.size(); i++) {
				if (dataSets.get(i) != null)
					updateDataset(i);
			}
			setChanged();
			notifyObservers(UpdateEvent.CHANGE_EQC);
		}
	}

	/**
	 * Returns the MCEqCircuit by ID
	 * 
	 * @param eqcID
	 *            ID of MCEqCircuit
	 * @return MCEqCircuit
	 */
	public MCEqCircuit getEquivalentCircuit(int eqcID) {
		return eqCircuits.get(eqcID);
	}

	/**
	 * Returns an int array with the currently available equivalent circuit
	 * models
	 * 
	 * @return int array with eqc IDs
	 */
	public int[] getModelIDs() {
		int[] tmp = new int[eqCircuits.size()];
		int j = 0;
		for (int i = 0; i < eqCircuits.size(); i++) {
			if (eqCircuits.get(i) != null) {
				tmp[j++] = i;
			}
		}
		int[] res = new int[j];
		System.arraycopy(tmp, 0, res, 0, j);
		return res;
	}

	/**
	 * Removes an equivalent circuit
	 * 
	 * @param eqcID
	 *            ID to the circuit
	 */
	public void removeEqCircuit(int eqcID) {
		if (eqcID < eqCircuits.size())
			this.eqCircuits.set(eqcID, null);
		setChanged();
		notifyObservers(UpdateEvent.REMOVE_EQC);
	}

	/**
	 * Checks if the eqcircuit given by the id exists
	 * 
	 * @param id
	 *            ID to the eqcircuit
	 * @return true if existing, flase if not
	 */
	public boolean isEqCircuit(int id) {
		try {
			if (this.eqCircuits.get(id) != null)
				return true;
			else
				return false;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Updates a equivalent circuits parameters in the model
	 * 
	 * @param eqcID
	 *            id of the model
	 * @param parameters
	 *            parameter list
	 */
	public void updateEqcParams(int eqcID, double[] parameters) {
		eqCircuits.get(eqcID).setParameters(parameters);
		// update the associated plots
		for (int i = 0; i < dataSets.size(); i++) {
			updateDataset(i);
		}
		setChanged();
		notifyObservers(UpdateEvent.CHANGE_EQC);
	}

	/**
	 * Update a dataset
	 * 
	 * @param i
	 *            id to the dataset
	 */
	private void updateDataset(Integer i) {
		if (dataSets.get(i) == null)
			return;
		if (dataSets.get(i).isRectPlotDataSet()) {
			if (dataSets.get(i) != null && dataSets.get(i).getRNM().src != DataSource.FILE) {
				RectPlotNewMeasurement nm = dataSets.get(i).getRNM();
				dataSets.set(i, buildDataSetRaw(nm));
			}
		} else {
			if (dataSets.get(i) != null && dataSets.get(i).getSNM().src != DataSource.FILE) {
				SmithChartNewMeasurement nm = dataSets.get(i).getSNM();
				dataSets.set(i, buildSmithChartDataSetRaw(nm));
			}
		}
	}

	/**
	 * Starts the optimizer of the eqcircuit
	 * 
	 * @param eqcID
	 *            id
	 */
	public void optimizeEqCircuit(int eqcID) {
		// Create worker, set data and start it
		worker = new MCWorker(this, "MCWorker-EQCOptimizer-" + eqcID);
		worker.setEQCircuit(eqCircuits.get(eqcID));
		worker.start();
	}

	/**
	 * Stops the worker and deletes all eqc related data
	 */
	public void killWorker() {
		worker.stopWork();
		worker = null;
	}

	public RFData getRFData() {
		return this.rfDataFile;
	}
}

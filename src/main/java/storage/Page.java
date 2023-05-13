package storage;

import constants.Constants;
import exceptions.DBAppException;
import storage.index.OctreeIndex;
import util.PagePrinter;
import util.filecontroller.*;
import util.search.PageSearch;
import java.io.IOException;
import java.io.Serializable;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import app.Action;

public class Page implements Serializable {
	/**
	 *
	 */
	private static final long serialVersionUID = 2857345022460368698L;
	private String name;
	private int maxRows;
	private Vector<Tuple> tuples;
	private Object minPK, maxPK;
	private Table table;

	public Page(Table table) throws DBAppException {
		this.tuples = new Vector<>();
		this.table = table;
		Properties prop = ConfigReader.readProperties();
		maxRows = Integer.parseInt(prop.getProperty(Constants.MAX_ROWS_IN_PAGE));
	}

	public Object getMinPK() {
		return minPK;
	}

	public Vector<Tuple> getTuples() {
		return tuples;
	}

	public void setMinPK(Object minPK) {
		this.minPK = minPK;
	}

	public Object getMaxPK() {
		return maxPK;
	}

	public void setMaxPK(Object maxPK) {
		this.maxPK = maxPK;
	}

	public int getSize() {
		return tuples.size();
	}

	public String getTableName() {
		return table.getName();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}


	public boolean isEmpty() {
		return tuples.size() == 0;
	}

	public boolean isFull() {
		return tuples.size() == maxRows;
	}

	protected Tuple removeLastTuple() throws DBAppException {
		Tuple ret = tuples.remove(tuples.size() - 1);
		Serializer.serializePage(name, this);
		return ret;
	}
	
	public Vector<Tuple> select(Hashtable<String, Object> colNameValue, String operator) {
		return PageSearch.linearSearchWithOperator(this, operator, colNameValue);
	}

	protected void insertIntoPage(Tuple tuple) throws DBAppException {
		int position = isEmpty() ? 0 : pageBinarySearch(tuple.getPrimaryKey());
		tuples.add(position, tuple);
		newMinMax();
		populateToIndex(tuple, Action.INSERT);
		Serializer.serializePage(name, this);
	}

	private void newMinMax() {
		if (tuples.size() > 0) {
			minPK = tuples.get(0).getPrimaryKey();
			maxPK = tuples.get(tuples.size() - 1).getPrimaryKey();
		}
	}

	public int pageBinarySearch(Object primaryKey) throws DBAppException {
		return PageSearch.binarySearch(this, primaryKey);
	}

	protected Vector<Tuple> linearSearch(Hashtable<String, Object> colNameValue) throws DBAppException {
		return PageSearch.linearSearch(this, colNameValue);
	}

	protected void deleteFromPage(Tuple tuple) throws DBAppException {
		int position = pageBinarySearch(tuple.getPrimaryKey());
		tuples.remove(position);
		newMinMax();
		populateToIndex(tuple, Action.DELETE);
		Serializer.serializePage(name, this);
		handleEmptyPage();

	}

	private void handleEmptyPage() {
		if (tuples.isEmpty()) {
			deletePageFile();
		}
	}

	private void deletePageFile() {
		FileDeleter.deleteFile(this, FileType.PAGE);
	}

	protected void createPageFile() throws DBAppException {

		try {
			FileCreator.createFile(this, FileType.PAGE);
		} catch (IOException e) {
			throw new DBAppException(e.getMessage());
		}
	}

	protected void updateTuple(Object clusteringKeyValue, Hashtable<String, Object> htblColNameValue)
			throws DBAppException {
		
		int pkVectorPoition = pageBinarySearch(clusteringKeyValue);
		Tuple oldTuple = tuples.get(pkVectorPoition);
		populateToIndex(oldTuple, Action.DELETE);
		
		for (Cell c : oldTuple.getCells()) {
			if (htblColNameValue.get(c.getKey()) != null)
				c.setValue(htblColNameValue.get(c.getKey()));
		}
		
		Tuple newTuple = table.createTuple(htblColNameValue);
		populateToIndex(newTuple, Action.INSERT);
		Serializer.serializePage(name, this);
	}
	
	private void populateToIndex(Tuple tuple, Action action) throws DBAppException {

		Vector<OctreeIndex<?>> indices = table.getIndices();

		for (OctreeIndex<?> index : indices) {
			if (action == Action.INSERT) {
				index.add(this, tuple);

			} else {
				index.remove(this, tuple);
			}
		}
	}

	public void print() throws DBAppException {
		PagePrinter print = new PagePrinter(this);
		print.printPage();
	}
	
}

package org.bimserver.serializers.json;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.bimserver.emf.IdEObject;
import org.bimserver.models.ifc2x3.IfcColumn;
import org.bimserver.models.ifc2x3.IfcDoor;
import org.bimserver.models.ifc2x3.IfcOpeningElement;
import org.bimserver.models.ifc2x3.IfcRoot;
import org.bimserver.models.ifc2x3.IfcSlab;
import org.bimserver.models.ifc2x3.IfcWall;
import org.bimserver.models.ifc2x3.IfcWallStandardCase;
import org.bimserver.models.ifc2x3.IfcWindow;
import org.bimserver.plugins.PluginException;
import org.bimserver.plugins.PluginManager;
import org.bimserver.plugins.ifcengine.IfcEngine;
import org.bimserver.plugins.ifcengine.IfcEngineException;
import org.bimserver.plugins.ifcengine.IfcEngineGeometry;
import org.bimserver.plugins.ifcengine.IfcEngineInstance;
import org.bimserver.plugins.ifcengine.IfcEngineInstanceVisualisationProperties;
import org.bimserver.plugins.ifcengine.IfcEngineModel;
import org.bimserver.plugins.serializers.EmfSerializer;
import org.bimserver.plugins.serializers.IfcModelInterface;
import org.bimserver.plugins.serializers.ProjectInfo;
import org.bimserver.plugins.serializers.SerializerException;
import org.eclipse.emf.ecore.EObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSONModelFormat2Serializer extends EmfSerializer {

	private static final Logger LOGGER = LoggerFactory.getLogger(JSONModelFormat2Serializer.class);
	private PrintWriter out;
	private IfcEngineModel ifcEngineModel;
	private IfcEngineGeometry geometry;

	public void init(IfcModelInterface model, ProjectInfo projectInfo, PluginManager pluginManager, IfcEngine ifcEngine) throws SerializerException {
		super.init(model, projectInfo, pluginManager, ifcEngine);
		try {
			EmfSerializer serializer = getPluginManager().requireIfcStepSerializer();
			serializer.init(model, getProjectInfo(), getPluginManager(), ifcEngine);
			ifcEngineModel = ifcEngine.openModel(serializer.getBytes());
			ifcEngineModel.setPostProcessing(true);
			geometry = ifcEngineModel.finalizeModelling(ifcEngineModel.initializeModelling());
		} catch (Exception e) {
			throw new SerializerException(e);
		}
	}

	@Override
	protected void reset() {
		setMode(Mode.BODY);
	}

	@Override
	public boolean write(OutputStream outputStream) throws SerializerException {
		if (getMode() == Mode.BODY) {
			out = new PrintWriter(outputStream);
			out.println("var model = [");
			writeGeometries();
			out.println();
			out.println("];");
			out.println();
			out.println("postMessage( model );");
			out.flush();

			setMode(Mode.FINISHED);
			return true;
		} else {
			return false;
		}
	}

	private String colorFromClass(Class ifcClass) {
		if (ifcClass == IfcWallStandardCase.class) {
			return "0xFF4400";
		} else if (ifcClass == IfcDoor.class) {
			return "0xFAFAFA";
		} else if (ifcClass == IfcWindow.class) {
			return "0xCCFFFF";
		} else if (ifcClass == IfcOpeningElement.class) {
			return "0x086CA2";
		} else {
			return "0x000000";
		}
	}

	private void writeGeometry(SetGeometryResult geometry, IfcRoot ifcRoot) {

		out.println("  'id' : '" + ifcRoot.getGlobalId().getWrappedValue() + "', ");
		out.println("  'type' : '" + ifcRoot.eClass().getName().toUpperCase() + "', ");
		out.println("  'geometry' : {");
		out.println("   'version' : 2, ");
		out.println("    'materials': [],");
		out.print("    'vertices': [ ");

		List<Float> vertices = geometry.getBinaryVertexBuffer().getVertices();
		if (vertices != null && vertices.size() > 0) {
			for (int i = 0; i < vertices.size(); i++) {
				out.print(i == 0 ? "" : ",");
				out.print(i % 3 == 0 ? " " : "");
				out.print(vertices.get(i));
			}
		}

		out.println("    ], ");
		out.print("    'normals':  [");

		List<Float> normals = geometry.getBinaryVertexBuffer().getNormals();
		if (normals != null && normals.size() > 0) {
			for (int i = 0; i < normals.size(); i++) {
				out.print(i == 0 ? "" : ",");
				out.print(i % 3 == 0 ? " " : "");
				out.print(normals.get(i));
			}
		}

		out.println("    ],");
		out.println("  'color':    " + colorFromClass(geometry.getIfcClass()) + " ,");
		out.println("    'uvs':      [ ],");
		out.print("    'faces': [ ");

		List<Integer> indices = geometry.getBinaryIndexBuffer().getIndices();
		if (indices != null && indices.size() > 0) {
			for (int i = 0; i < indices.size(); i += 3) {
				out.print(i == 0 ? "" : ",");
				out.print(" 32, ");
				out.print((indices.get(i)) + "," + (indices.get(i + 1)) + "," + (indices.get(i + 2)) + ",");
				out.print((indices.get(i)) + "," + (indices.get(i + 1)) + "," + (indices.get(i + 2)));
			}
		}

		out.println(" ]");
		out.println("    }");
		out.println();
	}

	private void writeGeometries() {
		List<SetGeometryResult> geometryList = new ArrayList<SetGeometryResult>();
		// Class[] eClasses = new Class[] { IfcSlab.class, IfcRoof.class,
		// IfcWall.class, IfcWallStandardCase.class, IfcWindow.class,
		// IfcDoor.class, IfcColumn.class, IfcRamp.class,
		// IfcStair.class, IfcStairFlight.class, IfcRailing.class };
		Class[] eClasses = new Class[] { IfcWallStandardCase.class, IfcWall.class, IfcWindow.class, IfcDoor.class, IfcSlab.class, IfcColumn.class };

		try {
			boolean first = true;
			for (Class<? extends EObject> eClass : eClasses) {
				for (Object object : model.getAll(eClass)) {
					IfcRoot ifcRoot = (IfcRoot) object;
					SetGeometryResult geometry = getGeometry(ifcRoot);
					if (geometry != null) {
						out.println(first ? "  {" : " ,{");
						first = false;
						writeGeometry(geometry, ifcRoot);
						out.print("  }");
					}
				}
			}
		} catch (SerializerException e) {
			LOGGER.error("", e);
		} catch (IfcEngineException e) {
			LOGGER.error("", e);
		}
	}

	private SetGeometryResult getGeometry(IdEObject ifcRootObject) throws SerializerException, IfcEngineException {
		BinaryIndexBuffer binaryIndexBuffer = new BinaryIndexBuffer();
		BinaryVertexBuffer binaryVertexBuffer = new BinaryVertexBuffer();
		int nrIndices = 0;
		IfcEngineInstance instance = ifcEngineModel.getInstanceFromExpressId((int)ifcRootObject.getOid());
		IfcEngineInstanceVisualisationProperties visualisationProperties = instance.getVisualisationProperties();
		for (int i = visualisationProperties.getStartIndex(); i < visualisationProperties.getPrimitiveCount() * 3 + visualisationProperties.getStartIndex(); i += 3) {
			binaryIndexBuffer.addIndex(geometry.getIndex(i));
			binaryIndexBuffer.addIndex(geometry.getIndex(i + 2));
			binaryIndexBuffer.addIndex(geometry.getIndex(i + 1));
			nrIndices++;
		}
		for (int i = 0; i < geometry.getNrVertices(); i += 3) {
			binaryVertexBuffer.addVertex(geometry.getVertex(i));
			binaryVertexBuffer.addVertex(geometry.getVertex(i + 1));
			binaryVertexBuffer.addVertex(geometry.getVertex(i + 2));
		}
		for (int i = 0; i < geometry.getNrNormals(); i += 3) {
			binaryVertexBuffer.addNormal(geometry.getNormal(i));
			binaryVertexBuffer.addNormal(geometry.getNormal(i + 1));
			binaryVertexBuffer.addNormal(geometry.getNormal(i + 2));
		}
		return new SetGeometryResult(nrIndices * 3, geometry.getNrVertices(), binaryIndexBuffer, binaryVertexBuffer);
	}
}
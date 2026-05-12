import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools

// MAKE SURE TO SELECT whole_cell ANNOTATIONS BEFORE RUNNING

// --- SETTINGS ---
def wholeCellClassName = "whole_cell" 
def membraneClassName = "Membrane"   // Updated to use your new Membrane boundary
def periClassName = "Perinuclear"    // Ensure this matches exactly with your generated class
def dapiClassName = "DAPI"
def cytoClassName = "cytoplasm"
// ----------------

def allAnnotations = getAnnotationObjects()
def wholeCells = allAnnotations.findAll { it.getPathClass() == getPathClass(wholeCellClassName) }
def membraneObjs = allAnnotations.findAll { it.getPathClass() == getPathClass(membraneClassName) }
def periObjs = allAnnotations.findAll { it.getPathClass() == getPathClass(periClassName) }
def dapiObjs = allAnnotations.findAll { it.getPathClass() == getPathClass(dapiClassName) }

// Fallback if "whole_cell" class isn't found
if (wholeCells.isEmpty()) {
    wholeCells = getSelectedObjects().findAll { it.isAnnotation() }
    if (wholeCells.isEmpty()) {
        print("Error: Could not find any annotations classified as '${wholeCellClassName}', and nothing is selected.")
        return
    }
}

def cytoClass = getPathClass(cytoClassName)
def newObjects = []

// Safely loop through every whole_cell
wholeCells.each { cell ->
    def roi = cell.getROI()
    if (roi != null) {
        def plane = roi.getImagePlane()
        def cytoGeom = roi.getGeometry() 
        
        // 1. Subtract Membrane boundary (This sets the outer limit for the cytoplasm)
        membraneObjs.each { membrane -> 
            def geom = membrane.getROI().getGeometry()
            if (cytoGeom.intersects(geom)) cytoGeom = cytoGeom.difference(geom)
        }
        
        // 2. Subtract Perinuclear donut
        periObjs.each { peri -> 
            def geom = peri.getROI().getGeometry()
            if (cytoGeom.intersects(geom)) cytoGeom = cytoGeom.difference(geom)
        }
        
        // 3. Subtract DAPI nucleus
        dapiObjs.each { dapi -> 
            def geom = dapi.getROI().getGeometry()
            if (cytoGeom.intersects(geom)) cytoGeom = cytoGeom.difference(geom)
        }
        
        // Create the final Cytoplasm annotation
        if (!cytoGeom.isEmpty()) {
            def cytoRoi = GeometryTools.geometryToROI(cytoGeom, plane)
            def cytoAnno = PathObjects.createAnnotationObject(cytoRoi, cytoClass)
            newObjects << cytoAnno
        }
    }
}

// Add the final Cytoplasm objects to the image
addObjects(newObjects)
print("Success! Generated ${newObjects.size()} Cytoplasm annotations.")
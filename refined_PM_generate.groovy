import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools

// MAKE SURE TO SELECT YOUR 'whole_cell' ANNOTATIONS BEFORE RUNNING

// --- SETTINGS ---
// Set the thickness. Note: For touching cells, a 1.0um shrink means 
// Cell A gives 1um and Cell B gives 1um, creating a 2.0um shared wall.
def membraneThicknessMicrons = 1.0 
// ----------------

// 1. Get image calibration
def imageData = getCurrentImageData()
def cal = imageData.getServer().getPixelCalibration()
def pixelWidth = cal.getPixelWidthMicrons()

if (Double.isNaN(pixelWidth)) {
    pixelWidth = 1.0 
    print("Warning: Image is not calibrated. Using pixels instead of microns.")
}

def shrinkPixels = membraneThicknessMicrons / pixelWidth

// 2. Get the selected whole_cell annotations to act as our "cookie cutters"
def selectedAnnos = getSelectedObjects().findAll { it.isAnnotation() }

if (selectedAnnos.isEmpty()) {
    print("Error: Please select at least one whole_cell annotation first!")
    return
}

// 3. Define the new class
def membraneClass = getPathClass("Membrane")
def newMembranes = []

// Grab all QuPath cell detections in the image to process
def allCells = getCellObjects()

if (allCells.isEmpty()) {
    print("Error: No QuPath cell detections found. Run cell detection first!")
    return
}

// 4. Loop through each whole_cell annotation
for (anno in selectedAnnos) {
    def annoGeom = anno.getROI().getGeometry()
    def plane = anno.getROI().getImagePlane()
    
    // Find all QuPath cells that sit inside this specific whole_cell annotation
    def insideCells = allCells.findAll { cell -> 
        def roi = cell.getROI()
        return roi != null && annoGeom.intersects(roi.getGeometry())
    }
    
    // Process each cell
    for (cell in insideCells) {
        def cellGeom = cell.getROI().getGeometry()
        
        // --- THE MAGIC STEP ---
        // Clip the QuPath cell geometry so it cannot expand past the whole_cell boundary.
        // This instantly fixes the "facing background" overestimation.
        def clippedCellGeom = cellGeom.intersection(annoGeom)
        
        // Shrink the newly clipped geometry inward
        def shrunkenGeom = clippedCellGeom.buffer(-shrinkPixels)
        
        // Skip if the cell is too small
        if (shrunkenGeom.isEmpty()) continue
        
        // Subtract the shrunken inner area from the clipped outer shape
        def membraneGeom = clippedCellGeom.difference(shrunkenGeom)
        
        // Convert back into a QuPath ROI and create the annotation
        def membraneRoi = GeometryTools.geometryToROI(membraneGeom, plane)
        def membraneAnnotation = PathObjects.createAnnotationObject(membraneRoi, membraneClass)
        
        newMembranes << membraneAnnotation
    }
}

// 5. Add the newly created, perfectly clipped membranes to the image
addObjects(newMembranes)
print("Success! Created ${newMembranes.size()} precision-clipped Membrane annotations.")
import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools

// MAKE SURE TO SELECT WHOLE_CELL ANNOTATIONS BEFORE RUNNING SCRIPT

// --- SETTINGS ---
// Set how thick you want the inward boundary ring to be (in microns)
def shrinkDistanceMicrons = 1.0
// ----------------

// 1. Get image calibration to convert microns to pixels
def imageData = getCurrentImageData()
def cal = imageData.getServer().getPixelCalibration()
def pixelWidth = cal.getPixelWidthMicrons()

// Fallback just in case your image isn't properly calibrated in microns
if (Double.isNaN(pixelWidth)) {
    pixelWidth = 1.0 
    print("Warning: Image is not calibrated. Using pixels instead of microns.")
}

def shrinkPixels = shrinkDistanceMicrons / pixelWidth

// 2. Get the currently selected annotations
def selected = getSelectedObjects()
def annotations = selected.findAll { it.isAnnotation() }

if (annotations.isEmpty()) {
    print("Error: Please select at least one whole_cell annotation on the image first!")
    return
}

// 3. Define the new class
def boundaryClass = getPathClass("cell_boundary")
def newObjects = []

// 4. Loop through selected annotations, shrink, and subtract the inner part
for (anno in annotations) {
    def roi = anno.getROI()
    if (roi == null) continue
    
    def plane = roi.getImagePlane()
    
    // Convert the QuPath ROI to a standard spatial geometry shape
    def originalGeom = roi.getGeometry()
    
    // Shrink the geometry inward (using a negative buffer)
    def shrunkenGeom = originalGeom.buffer(-shrinkPixels)
    
    // Safety check: Skip if the cell was so small that shrinking it erased it entirely
    if (shrunkenGeom.isEmpty()) {
        continue
    }
    
    // Subtract the shrunken inner area from the original outer shape (creates the inward donut)
    def boundaryGeom = originalGeom.difference(shrunkenGeom)
    
    // Convert the math geometry back into a QuPath ROI using your working method
    def boundaryRoi = GeometryTools.geometryToROI(boundaryGeom, plane)
    
    // Create the physical annotation object using PathObjects
    def boundaryAnnotation = PathObjects.createAnnotationObject(boundaryRoi, boundaryClass)
    newObjects << boundaryAnnotation
}

// 5. Add the newly created boundaries to the image
addObjects(newObjects)
print("Success! Created ${newObjects.size()} cell_boundary annotations.")
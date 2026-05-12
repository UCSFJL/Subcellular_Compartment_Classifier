import qupath.lib.objects.PathObjects
import qupath.lib.roi.GeometryTools

// MAKE SURE TO SELECT DAPI ANNOTATIONS BEFORE RUNNING SCRIPT

// --- SETTINGS ---
// Set how far outward and inward you want the perinuclear ring to go (in microns)
def outwardDistanceMicrons = 0.3
def inwardDistanceMicrons = 0.2
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

def outwardPixels = outwardDistanceMicrons / pixelWidth
def inwardPixels = inwardDistanceMicrons / pixelWidth

// 2. Get the currently selected annotations
def selected = getSelectedObjects()
def annotations = selected.findAll { it.isAnnotation() }

if (annotations.isEmpty()) {
    print("Error: Please select at least one DAPI annotation on the image first!")
    return
}

// 3. Define the new class
def perinuclearClass = getPathClass("Perinuclear")
def newObjects = []

// 4. Loop through selected annotations, expand, shrink, and subtract
for (anno in annotations) {
    def roi = anno.getROI()
    if (roi == null) continue
    
    def plane = roi.getImagePlane()
    
    // Convert the QuPath ROI to a standard spatial geometry shape
    def originalGeom = roi.getGeometry()
    
    // Expand the geometry outward for the outer ring
    def expandedGeom = originalGeom.buffer(outwardPixels)
    
    // Shrink the geometry inward (using a negative buffer) for the inner hole
    def shrunkenGeom = originalGeom.buffer(-inwardPixels)
    
    // Subtract the shrunken inner boundary from the expanded outer boundary
    def donutGeom = expandedGeom.difference(shrunkenGeom)
    
    // Convert the math geometry back into a QuPath ROI using your correct method
    def donutRoi = GeometryTools.geometryToROI(donutGeom, plane)
    
    // Create the physical annotation object using PathObjects
    def donutAnnotation = PathObjects.createAnnotationObject(donutRoi, perinuclearClass)
    newObjects << donutAnnotation
}

// 5. Add the newly created donuts to the image
addObjects(newObjects)
print("Success! Created ${newObjects.size()} Perinuclear annotations.")
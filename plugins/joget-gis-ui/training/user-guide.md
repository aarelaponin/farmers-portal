# Map Boundary Capture — User Guide

A step-by-step guide for capturing, editing, and fixing land boundaries.

---

## Table of Contents

1. [Getting Started](#1-getting-started)
2. [Capture a New Boundary — Walk Mode](#2-capture-a-new-boundary--walk-mode)
3. [Capture a New Boundary — Draw Mode](#3-capture-a-new-boundary--draw-mode)
4. [Edit an Existing Boundary](#4-edit-an-existing-boundary)
5. [Fix Problems](#5-fix-problems)
6. [Understanding the Map Controls](#6-understanding-the-map-controls)
7. [Understanding the Information Panel](#7-understanding-the-information-panel)

---

## 1. Getting Started

### What This Tool Does

This tool lets you capture land boundaries on a map. You can:

- **Walk the boundary** using GPS on your phone (Walk Mode)
- **Draw the boundary** by clicking on a map on your computer (Draw Mode)

The tool saves the boundary shape and calculates:

- **Area** in hectares
- **Perimeter** (distance around) in meters
- **Centre point** of the boundary
- **Number of corners**

### What You Need

**For Walk Mode (field work):**
- A mobile phone with GPS
- GPS turned ON in phone settings
- Internet connection (for the map)

**For Draw Mode (office work):**
- A computer with internet
- A web browser

### Opening the Map

1. Open the form in your browser
2. Scroll to the map section
3. You will see either:
   - An empty map with a **"Capture Boundary"** button (new record)
   - A map showing an existing boundary (editing a record)

---

## 2. Capture a New Boundary — Walk Mode

Use this when you are **at the location** with your phone.

### Step 1: Start Capture

Tap the **Capture Boundary** button. If you see two choices, tap **Walk the Boundary**.

### Step 2: Check GPS Signal

A GPS panel appears in the top-right corner. It shows:

- A coloured bar showing signal quality
- Your accuracy in meters (for example: ±5m)
- A status message

**Wait for green or yellow** before you start marking corners.

| Bar Colour | Accuracy     | Status                       | Can You Mark? |
|------------|--------------|------------------------------|---------------|
| Green      | 3m or less   | Excellent — Ready to mark    | Yes           |
| Green      | 5m or less   | Good — Ready to mark         | Yes           |
| Yellow     | 10m or less  | Fair — Wait if possible      | Yes           |
| Orange     | 20m or less  | Poor — Move to open area     | No            |
| Red        | Over 20m     | Very poor — Check GPS        | No            |

**To improve GPS:**
- Move away from buildings, trees, and walls
- Stand still for a few seconds
- Make sure GPS is turned ON in your phone settings

### Step 3: Walk to the First Corner

Go to the first corner of the boundary. Stand still.

### Step 4: Mark the Corner

Tap the **Mark Corner** button at the bottom of the screen.

Your phone will vibrate. A message says **"Corner 1 marked"**. A marker appears on the map.

### Step 5: Walk to Each Corner

Walk along the boundary to the next corner. Tap **Mark Corner** at each corner.

**Important:**
- Walk in one direction around the whole boundary (clockwise or counter-clockwise)
- Stand still when you tap Mark Corner
- You need at least 3 corners

### Step 6: Close the Boundary

When you walk back near your starting point, a yellow message appears:

> "Close to start point. Close Polygon"

Tap the **Close Polygon** button. The boundary closes and becomes a blue shape on the map.

### Step 7: Check the Results

The bottom panel shows:

- **Area** — size in hectares
- **Perimeter** — distance around the boundary in meters
- **Corners** — number of corners you marked
- **GPS Avg** — average accuracy of your GPS readings

### Step 8: Handle Warnings

The tool checks your boundary for problems:

- **Green message: "Boundary captured successfully"** — Everything is fine.
- **Red message** — There is a problem. See [Fix Problems](#5-fix-problems).
- **Orange overlap warning** — Your boundary touches another boundary. See [Overlap Warnings](#overlap-warnings).

### Step 9: Save

If everything looks correct, submit the form to save the boundary.

If you want to try again, tap **Redraw** to start over.

---

## 3. Capture a New Boundary — Draw Mode

Use this when you are **at a computer** in the office.

### Step 1: Start Capture

Click the **Capture Boundary** button. If you see two choices, click **Draw on Map**.

### Step 2: Navigate to the Location

Find the right area on the map:

- **Search** — Click the search icon (top-left). Type a place name. Click a result to go there.
- **Zoom** — Use your mouse scroll wheel, or click + / - on the map.
- **Pan** — Click and drag the map to move around.
- **Switch view** — Click the **Layers** button (bottom-left) to use Satellite view. This shows real land and buildings.

### Step 3: Draw the Boundary

Click on the map at each corner of the boundary.

- Each click adds a numbered marker (1, 2, 3, ...)
- A blue line connects the markers
- A dotted line follows your mouse showing the next edge
- The bottom panel updates with area and perimeter as you draw

**Tips for accurate drawing:**
- Zoom in close to the boundary
- Use Satellite view to see the land clearly
- Place corners where the boundary changes direction
- For straight edges, you only need a corner at each end

### Step 4: Close the Boundary

When you have placed all corners, close the shape:

- **Click on the first corner** — When your mouse is near the first (green) corner, it gets bigger and shows "Click to close". Click it.
- **Or double-click** anywhere on the map.
- **Or click the "Complete" button** in the toolbar.

The boundary becomes a filled blue shape.

### Step 5: Check the Results

The bottom panel shows:

- **Area** — size in hectares
- **Perimeter** — distance around the boundary in meters
- **Corners** — number of corners

### Step 6: Edit if Needed

After closing, you can still make changes:

**Delete a corner:**
1. Click on a corner marker — it turns red
2. Press the **Delete** key on your keyboard
3. The corner is removed (you must keep at least 3)

**Add a corner:**
1. Look for the small diamond shapes on each edge
2. Click and drag a diamond to create a new corner

**Undo the last corner (while drawing):**
- Click the **Undo** button, or press **Ctrl+Z**

**Start over:**
- Click the **Redraw** button

### Step 7: Handle Warnings

Same as Walk Mode — see Step 8 above.

### Step 8: Save

If everything looks correct, submit the form to save the boundary.

---

## 4. Edit an Existing Boundary

When you open a record that already has a boundary, the map shows the saved boundary.

### Make Changes

1. Click the **Edit** button in the toolbar
2. The boundary becomes editable:
   - **Delete corners** — Click a corner (turns red), press Delete
   - **Add corners** — Drag diamond shapes on edges
3. When done editing, the boundary auto-saves when you submit the form

### Redraw Completely

1. Click the **Redraw** button
2. This removes the old boundary
3. Draw or walk a new boundary from scratch

### Overlap Warnings When Editing

When you edit a boundary, the tool checks for overlaps with other boundaries.

**It automatically ignores overlaps with the original boundary.** This means:

- If you make the boundary smaller — no false warning
- If you make it bigger — no false warning
- If you shift it slightly — no false warning

Only **real overlaps** with a **different** boundary trigger a warning.

---

## 5. Fix Problems

### Crossing Lines (Self-Intersection)

**What you see:** Red lines on the map and a red message: "Boundary lines are crossing"

**What it means:** Two edges of your boundary cross over each other. This creates an invalid shape.

**How to fix:**

*In Draw Mode:*
- Look at the red crossing point on the map
- Click on a corner near the crossing — it turns red
- Press **Delete** to remove it
- Add new corners in the correct position
- Or click **Redraw** to start over

*In Walk Mode:*
- Tap **Redraw** and walk the boundary again
- Make sure you walk the corners in order (do not skip corners or walk back and forth)

### Overlap with Another Boundary

**What you see:** An orange warning panel that says "Overlap Detected"

**What it means:** Your boundary overlaps with one or more existing boundaries. The panel shows:

- Which records overlap
- How much area overlaps
- What percentage overlaps

**On the map:** Overlapping areas are shown in red.

**Your options:**

1. **Adjust Boundary** — Click this button to go back and edit your boundary so it does not overlap
2. **Save Anyway** — Click this button if the overlap is correct and expected

### Area Too Small or Too Large

**What you see:** A yellow warning about area size.

**How to fix:**
- If too small — add more corners or move corners outward
- If too large — remove corners or move corners inward

### Too Many Corners

**What you see:** A warning about the number of corners approaching the limit.

**How to fix:** Use fewer corners. You only need corners where the boundary changes direction. Long straight edges need just one corner at each end.

---

## 6. Understanding the Map Controls

### Layers Button (bottom-left)

Switch between different map views:

| View | Best For |
|------|----------|
| **OpenStreetMap** | General navigation, roads, and place names |
| **Satellite** | Seeing actual land, fields, and buildings |
| **Hybrid** | Satellite with road names on top |
| **Terrain** | Seeing hills, valleys, and elevation |

### Search (top-left)

1. Click the search icon
2. Type a place name (village, town, district)
3. Select a result from the list
4. The map moves to that location

### Nearby Parcels (if available)

- Click **Show Nearby** to see other boundaries near your area
- These appear as grey outlines on the map
- They are read-only — you cannot edit them
- Click **Hide Nearby** to remove them

### Network Status (top-left)

- **Green dot + "Online"** — Internet is connected
- **Red dot + "Offline"** — No internet. The map tiles may not load, but you can still mark corners in Walk Mode.

---

## 7. Understanding the Information Panel

The panel at the bottom of the map shows boundary details.

### While Drawing / Walking

| Field | Description |
|-------|-------------|
| **Area** | Size of the boundary in hectares. Updates as you add corners. |
| **Perimeter** | Distance around the boundary in meters. |
| **Corners** | How many corners you have marked. |

### After Closing (Walk Mode only)

| Field | Description |
|-------|-------------|
| **GPS Avg** | Average GPS accuracy of all your marked corners. Lower is better. |

### Validation Messages

| Colour | Meaning |
|--------|---------|
| **Green** | Everything is fine. Boundary captured successfully. |
| **Yellow** | Warning. The boundary is valid but something may need attention (area size). |
| **Red** | Error. The boundary has a problem that needs to be fixed (crossing lines, not enough corners). |

---

## Quick Tips Summary

1. **Use Satellite view** to see the real land when drawing
2. **Zoom in** for better accuracy
3. **Walk in one direction** around the boundary (do not go back and forth)
4. **Wait for green GPS** before marking corners
5. **Stand still** when marking a corner in Walk Mode
6. **Place corners where the boundary changes direction** — straight edges need only two corners
7. **Use Undo** (Ctrl+Z) if you make a mistake in Draw Mode
8. **Check the area** in the panel — does it match what you expect?
9. **Fix red warnings** before saving — yellow warnings are optional
10. **Use Redraw** if the boundary is too wrong to fix by editing
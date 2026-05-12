# Farmer Search Tool — End-User Guide

**Version:** 8.1
**Last Updated:** 2026-02-01

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Quick Search — By National ID](#2-quick-search--by-national-id)
3. [Quick Search — By Phone Number](#3-quick-search--by-phone-number)
4. [Advanced Search — By Name](#4-advanced-search--by-name)
5. [Working with Results](#5-working-with-results)
6. [Recent Farmers](#6-recent-farmers)
7. [Clearing and Changing Selection](#7-clearing-and-changing-selection)
8. [Offline and Connection Problems](#8-offline-and-connection-problems)
9. [Tips for Better Searches](#9-tips-for-better-searches)
10. [Common Questions (FAQ)](#10-common-questions-faq)
11. [Quick Reference Card](#11-quick-reference-card)

---

## 1. Introduction

The **Farmer Search** tool helps you find a registered farmer in the system by name, national ID, or phone number. It appears as a field inside a Joget form — you type information, and it finds the matching farmer for you.

### Where it appears

The Farmer Search field looks like a text box with a **Search** button. When no farmer is selected, you will see the placeholder text *"Enter ID, phone, or name..."*. Once a farmer is selected, the field shows the farmer's name with an **X** button to clear the selection.

### What you need

- A web browser (Chrome, Firefox, Safari, or Edge)
- A Joget login with access to the form
- An internet connection for searching (recent farmers can be used offline)

---

## 2. Quick Search — By National ID

This is the fastest way to find a farmer if you know their national ID.

**Steps:**

1. Click inside the search field (where it says *"Enter ID, phone, or name..."*)
2. Type the full national ID (9 to 13 digits)
3. A blue **ID** badge appears next to your input, confirming the system recognized it as an ID
4. The system automatically looks up the farmer — no need to click Search
5. If a match is found, the farmer is selected instantly. The field shows the farmer's name

`[Screenshot: inline field showing typed ID number with "ID" badge]`

`[Screenshot: field after auto-selection showing farmer name and X button]`

**Tips:**
- You must type the **complete** ID. A partial number will not trigger an instant match
- If no farmer is found, the field shows *"No farmer found"* — double-check the number and try again
- If the match is uncertain, the full search dialog opens so you can confirm

---

## 3. Quick Search — By Phone Number

If you know the farmer's phone number, you can search by that instead.

**Steps:**

1. Click inside the search field
2. Type the phone number (8 or more digits)
3. A **Phone** badge appears, confirming the system recognized it as a phone number
4. The system automatically looks up the farmer
5. If a match is found, the farmer is selected instantly

`[Screenshot: inline field showing typed phone number with "Phone" badge]`

**Tips:**
- Works with or without the country code — both `+26622123456` and `22123456` are accepted
- The number must be at least 8 digits to trigger the instant lookup
- If no match is found, the message *"No farmer found"* appears briefly

---

## 4. Advanced Search — By Name

When you don't have an exact ID or phone number, use the full search dialog to find a farmer by name and location.

### Opening the search dialog

There are two ways to open the search dialog:

- Click the **Search** button on the right side of the field
- Type a name in the field and press **Enter** — the dialog opens with your name pre-filled

`[Screenshot: full search dialog with annotated areas — header, recent farmers, search input, district dropdown, criteria area, confidence bar, results area]`

### Dialog layout (top to bottom)

| Area | What it does |
|------|-------------|
| **Header** | Shows "Find Farmer" title, online/offline status, and close button |
| **Recent Farmers** | Quick list of farmers you've selected before (see [Section 6](#6-recent-farmers)) |
| **OR SEARCH divider** | Separates recent farmers from the search form |
| **Search input** | Type the farmer's name, ID, or phone here |
| **District dropdown** | Filter by district (Berea, Butha-Buthe, Leribe, Mafeteng, Maseru, Mohale's Hoek, Mokhotlong, Qacha's Nek, Quthing, Thaba-Tseka) |
| **Additional Criteria** | Add extra filters like village, cooperative, etc. |
| **Confidence bar** | Shows how likely the search is to give good results |
| **Results area** | Displays matching farmers after you click Search |

### 4.1 Name + District Search

This is the most common search method.

**Steps:**

1. Type the farmer's first or last name in the search input
2. Select a district from the **District** dropdown (defaults to *"All districts"*)
3. Watch the confidence bar — it updates as you type
4. When the bar shows an acceptable level, click the **Search** button

`[Screenshot: name typed in search input, district selected, confidence bar at amber level]`

**Important:** A name alone is not enough — the Search button stays grayed out until you also select a district, village, or other filter. You will see the message *"Please add district or village"* as a reminder.

### 4.2 Adding More Criteria

You can narrow your search by adding extra filters.

**Steps:**

1. Click the **+ Add criteria** button below the district dropdown
2. A menu appears with these options:
   - **Village** — requires a district to be selected first
   - **Community Council**
   - **Cooperative**
   - **Partial ID** (4+ digits from the farmer's ID)
   - **Partial Phone** (8+ digits from the farmer's phone)
3. Select a criterion from the menu
4. A new filter row appears — type or select a value
5. For Village, Community Council, and Cooperative, an autocomplete dropdown appears as you type (start typing at least 2 characters)
6. To remove a criterion, click the **X** button on the right side of that row

`[Screenshot: criteria builder with Village autocomplete dropdown showing suggestions]`

**Notes:**
- Village autocomplete only works after selecting a district — it shows villages within the selected district
- You can add multiple criteria at the same time
- Each criterion you add improves the confidence bar
- The confidence bar updates immediately as you change criteria

### 4.3 Understanding the Confidence Bar

The confidence bar estimates how likely your search criteria will find the right farmer.

| Bar Color | Confidence | What it means | Message shown |
|-----------|------------|---------------|---------------|
| **Green** | 70–100% | Good criteria — search should find the farmer | *"Ready to search"* |
| **Amber** | 40–69% | Acceptable but results may be broad | *"Add more criteria for better results"* |
| **Red** | Below 40% | Too broad — add more details | *"Please add criteria"* |

`[Screenshot: three confidence bar states — green at 85%, amber at 55%, red at 20%]`

- When you type a **full national ID** or **phone number**, the confidence jumps to **100%** and shows *"Exact ID match search"* or *"Exact phone match search"*
- The Search button is **disabled** (grayed out) when the confidence bar is red and criteria are insufficient
- After selecting **name + district** (without village), you may see: *"Results may be broad. Consider adding village."* — the search will still work, but adding a village gives better results

---

## 5. Working with Results

After clicking Search, the results appear below the search form.

### Result cards

Each result shows a **farmer card** with:

- **Name** — the farmer's first and last name
- **Location** — district and village (e.g., *Maseru > Ha Matala*)
- **ID** — the national ID (may be partially masked for privacy)
- **Gender icon** — male or female icon
- **Score badge** — a percentage showing how well this farmer matches your search

`[Screenshot: results list with 2–3 farmer cards showing name, location, ID, and score badge]`

### Score badge colors

| Score | Color | Meaning |
|-------|-------|---------|
| 90–100% | **Green** | Very strong match |
| 70–89% | **Yellow** | Good match |
| 50–69% | **Orange** | Possible match — review carefully |
| Below 50% | **Red** | Weak match — likely not the right farmer |

### Selecting a farmer

Click anywhere on a farmer card to select that farmer. The dialog closes and the farmer's name appears in the form field.

If there is only one result with a high score (90% or above), the system **automatically selects** the farmer for you. A brief notification appears: *"Farmer found and selected: [Name]"*.

### When no results are found

If the search returns no matches, you will see the message: *"No farmers found matching your criteria"*

The system may also show suggestions such as:
- Check the spelling of the name
- Try a different village or district
- Search by National ID or Phone instead

**What to do:**
1. Double-check the spelling of the name
2. Remove some filters — try fewer criteria to broaden the search
3. If possible, try searching by national ID or phone number instead

---

## 6. Recent Farmers

The **Recent Farmers** panel appears at the top of the search dialog. It shows up to 5 farmers you have previously selected, with the most recent at the top.

`[Screenshot: recent farmers panel showing 2–3 farmers with Select buttons]`

### How to use recent farmers

- Open the search dialog
- At the top you will see the **Recent Farmers** section (with a clock icon)
- Click **Select** next to any farmer to select them instantly
- You can also click anywhere on the farmer's row

### Key features

- **Persists across page refreshes** — your recent farmers are saved in the browser
- **Works offline** — when you have no internet connection, you can still select from recent farmers
- **Newest first** — the most recently selected farmer always appears at the top
- **Up to 5 farmers** — when you select a 6th farmer, the oldest one is removed
- **Clear all** — click the **Clear** button in the panel header to remove all recent farmers

**Tip:** If you know you will be visiting farmers in the field without reliable internet, search for those farmers first while connected. They will then appear in your recent farmers list for offline use.

---

## 7. Clearing and Changing Selection

### Clearing the current selection

When a farmer is selected, you will see their name displayed in the field with an **X** button next to it.

- Click the **X** button to clear the selection
- The field returns to the empty state showing *"Enter ID, phone, or name..."*
- You can now search for a different farmer

### Searching for a different farmer

After clearing, simply:
1. Type a new ID, phone, or name in the field, **or**
2. Click the **Search** button to open the dialog and search with filters

---

## 8. Offline and Connection Problems

### Online/Offline indicator

The search dialog header shows your connection status:

- **Green dot + "Online"** — connected, all features available
- **Gray dot + "Offline"** — no internet connection

### When you are offline

When offline, you will see a warning banner at the top of the dialog:

> *"You are offline. Search is not available. Select from recent farmers or wait for connection."*

- The search input, district dropdown, and criteria fields are **disabled**
- The Search button is **disabled**
- You can **still select from recent farmers** (they are stored in your browser)

When your connection comes back, the system detects this automatically and re-enables all features.

### Error messages and what to do

| Error message | What happened | What to do |
|---------------|--------------|------------|
| *"Unable to connect. Please check your internet connection."* | No network connection | Check your Wi-Fi or mobile data, then click **Retry** |
| *"Request timed out. Please try again."* | The server took too long | Click **Retry**, or try searching with more specific criteria |
| *"Server error. Please try again later."* | Something went wrong on the server | Wait a moment, then click **Retry** |
| *"Authentication failed. Please contact administrator."* | Your login session may have expired | Refresh the page and log in again. If the problem continues, contact your administrator |
| *"An unexpected error occurred."* | Unknown problem | Click **Retry**. If the problem continues, contact your administrator |

`[Screenshot: error message panel showing Retry and Dismiss buttons]`

When an error appears, you will see two buttons:

- **Retry** — tries the same search again (available for network, timeout, and server errors)
- **Dismiss** — closes the error message and returns to the search form

The system retries up to 3 times, with increasing wait time between attempts.

---

## 9. Tips for Better Searches

1. **Use the national ID when available** — it gives an instant, exact match with 100% confidence
2. **Phone number is the next best option** — also gives an instant exact match
3. **For name searches, always add a village** — this dramatically narrows results and raises confidence
4. **Don't worry about exact spelling** — the system uses fuzzy matching and will find names even with small spelling differences (e.g., searching "Tabo" will find "Thabo")
5. **Check the confidence bar before searching** — green means you have good criteria, amber means results may be broad, red means you need more details
6. **Use recent farmers to save time** — especially useful when visiting the same farmers repeatedly or working offline
7. **Remove filters if you get no results** — if a search returns nothing, try with fewer criteria (e.g., remove village and search by name + district only)
8. **Add a community council or cooperative** — these are good extra filters when village alone isn't enough
9. **Try partial ID or partial phone** — if you know some digits of the farmer's ID or phone number, adding them as criteria helps narrow results
10. **Search before going to the field** — look up farmers while you have a good internet connection so they appear in your recent farmers list for offline use

---

## 10. Common Questions (FAQ)

### Why is the Search button grayed out?

The Search button is disabled when your search criteria are not specific enough. Look at the message below the confidence bar for guidance:

- *"Enter search criteria"* — you haven't typed anything yet
- *"Please add district or village"* — a name alone is too broad; add a location filter
- *"Please add name or ID"* — a district alone is too broad; add a name or ID

Add more criteria until the message changes to *"Ready to search"* and the button becomes active.

### I searched by ID but the wrong farmer was selected. What do I do?

Click the **X** button next to the farmer's name to clear the selection, then search again. Double-check the national ID number — a single wrong digit will match a different farmer (or no farmer at all).

### My search returned too many results. How do I narrow them down?

Click **+ Add criteria** and add a **Village**, **Community Council**, or **Cooperative** filter. You can also add a **Partial ID** or **Partial Phone** if you know some digits.

### I know the farmer exists, but the search says "No farmers found." What should I check?

1. **Check the spelling** of the name — try different spellings or just the first few letters
2. **Check the district** — make sure you selected the right district
3. **Remove extra criteria** — the farmer might not match one of the filters you added
4. **Try searching by ID or phone** — if you have either, use that instead
5. **Ask your administrator** — the farmer may not be registered in the system yet

### Does this work on mobile phones and tablets?

Yes. The search dialog and all features work on mobile browsers. The layout adjusts to smaller screens automatically.

### What do the score percentages mean?

The score shows how closely a farmer matches your search criteria:

- **90–100%** — near-perfect or exact match (green badge)
- **70–89%** — good match with minor differences (yellow badge)
- **50–69%** — possible match but review carefully (orange badge)
- **Below 50%** — weak match, probably not the right farmer (red badge)

A score of 100% typically means an exact ID or phone match. Lower scores may result from fuzzy name matching or partial criteria matches.

### Can I use this without internet?

You can **select from recent farmers** without internet. However, you cannot perform new searches while offline. When you go back online, search is re-enabled automatically.

### How do I close the search dialog?

There are three ways:
- Click the **X** button in the top-right corner of the dialog
- Press the **Escape** key on your keyboard
- Click the dark area outside the dialog

---

## 11. Quick Reference Card

Print or screenshot this section for a quick reference while working.

### Search Methods

| What you have | What to do | Expected result |
|--------------|-----------|----------------|
| Full national ID (9–13 digits) | Type it in the field | Farmer auto-selected instantly |
| Full phone number (8+ digits) | Type it in the field | Farmer auto-selected instantly |
| Farmer's name + district | Open Search dialog, type name, select district, click Search | List of matching farmers |
| Farmer's name + village | Open Search dialog, type name, add Village criterion | Narrower, higher-confidence results |
| Partial ID or phone | Open Search dialog, add as extra criterion | Helps narrow name-based searches |
| Recently selected farmer | Open Search dialog, click Select in Recent Farmers | Instant selection (works offline) |

### Confidence Bar Colors

| Color | Range | Meaning |
|-------|-------|---------|
| Green | 70–100% | Good to search |
| Amber | 40–69% | Consider adding criteria |
| Red | 0–39% | Need more criteria |

### Score Badge Colors

| Color | Range | Meaning |
|-------|-------|---------|
| Green | 90–100% | Strong match |
| Yellow | 70–89% | Good match |
| Orange | 50–69% | Possible match |
| Red | Below 50% | Weak match |

### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| **Enter** | Trigger search (in search input) or confirm selection |
| **Escape** | Close the search dialog |
| **Tab** | Move between form fields and buttons |
| **Arrow keys** | Navigate autocomplete dropdown options |

### Troubleshooting

| Problem | Solution |
|---------|----------|
| Search button is grayed out | Add more criteria (name + district at minimum) |
| "No farmers found" | Check spelling, try different district, remove extra filters |
| "Unable to connect" | Check internet connection, click Retry |
| "Request timed out" | Click Retry, or add more criteria for a faster search |
| "Authentication failed" | Refresh the page and log in again |
| "Server error" | Wait a moment and click Retry |
| Dialog won't close | Press Escape or click the X button |
| Recent farmers not showing | They may have been cleared; select a farmer first to populate the list |
| Wrong farmer selected | Click X to clear, then search again with the correct ID |
| Village filter is disabled | Select a district first — village requires a district |

---

*This guide covers the Smart Farmer Search plugin v8.1. For technical issues, contact your system administrator.*

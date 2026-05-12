/**
 * Recent Farmers Manager - localStorage handling for PWA
 * Phase 6: Smart Farmer Search
 * 
 * Manages the list of recently selected farmers in localStorage
 * for quick access, especially useful in offline scenarios.
 * 
 * @version 8.1-SNAPSHOT-phase6
 */

(function(global) {
    'use strict';

    var VERSION = '8.1-SNAPSHOT-phase6';
    var STORAGE_KEY = 'fss_recent_farmers';
    var DEFAULT_MAX_ITEMS = 5;  // Match JSON default (Issue #2)

    console.log('[RecentFarmers] Loading v' + VERSION);

    /**
     * RecentFarmers - Manages recently selected farmers in localStorage
     * @param {number} maxItems - Maximum number of farmers to store (default: 10)
     */
    function RecentFarmers(maxItems) {
        this.maxItems = maxItems || DEFAULT_MAX_ITEMS;
        this.storageAvailable = this._checkStorageAvailable();
        
        if (!this.storageAvailable) {
            console.warn('[RecentFarmers] localStorage not available');
        }
    }

    /**
     * Check if localStorage is available
     * @private
     * @returns {boolean}
     */
    RecentFarmers.prototype._checkStorageAvailable = function() {
        try {
            var test = '__storage_test__';
            localStorage.setItem(test, test);
            localStorage.removeItem(test);
            return true;
        } catch (e) {
            return false;
        }
    };

    /**
     * Get all recent farmers
     * @returns {Array} Array of farmer objects sorted by timestamp desc (newest first)
     */
    RecentFarmers.prototype.getAll = function() {
        if (!this.storageAvailable) {
            return [];
        }

        try {
            var data = localStorage.getItem(STORAGE_KEY);
            if (!data) {
                return [];
            }
            
            var farmers = JSON.parse(data);
            
            // Ensure array and sort by timestamp descending
            if (!Array.isArray(farmers)) {
                return [];
            }
            
            return farmers.sort(function(a, b) {
                return (b.timestamp || 0) - (a.timestamp || 0);
            });
        } catch (e) {
            console.warn('[RecentFarmers] Error reading localStorage:', e);
            return [];
        }
    };

    /**
     * Add a farmer to the recent list
     * If farmer already exists (by ID), moves to front and updates timestamp
     * @param {Object} farmer - Farmer object with id, firstName, lastName, etc.
     * @returns {boolean} Success status
     */
    RecentFarmers.prototype.add = function(farmer) {
        if (!this.storageAvailable) {
            console.warn('[RecentFarmers] localStorage not available');
            return false;
        }

        if (!farmer) {
            console.warn('[RecentFarmers] Cannot add null farmer');
            return false;
        }

        // Determine unique identifier - prefer id, fallback to sourceRecordId or generate from nationalId+name
        var farmerId = farmer.id || farmer.sourceRecordId;
        
        // If still no ID, generate one from available data
        if (!farmerId) {
            // Use nationalId + firstName + lastName as composite key
            var nationalId = farmer.nationalId || '';
            var firstName = farmer.firstName || '';
            var lastName = farmer.lastName || '';
            
            if (nationalId || (firstName && lastName)) {
                farmerId = 'gen_' + (nationalId + '_' + firstName + '_' + lastName).replace(/[^a-zA-Z0-9]/g, '_');
                console.log('[RecentFarmers] Generated ID:', farmerId);
            } else {
                console.warn('[RecentFarmers] Cannot add farmer - no identifier available:', farmer);
                return false;
            }
        }

        console.log('[RecentFarmers] Adding farmer with ID:', farmerId);

        try {
            var farmers = this.getAll();
            
            // Remove existing entry if present (will re-add at front)
            farmers = farmers.filter(function(f) {
                return f.id !== farmerId;
            });

            // Create minimal entry for storage
            var entry = {
                id: farmerId,
                firstName: farmer.firstName || '',
                lastName: farmer.lastName || '',
                nationalId: farmer.nationalId || '',
                districtCode: farmer.districtCode || '',
                districtName: farmer.districtName || '',
                village: farmer.village || '',
                gender: farmer.gender || '',
                timestamp: Date.now()
            };

            // Add to front
            farmers.unshift(entry);

            // Enforce maximum items
            if (farmers.length > this.maxItems) {
                farmers = farmers.slice(0, this.maxItems);
            }

            // Save
            localStorage.setItem(STORAGE_KEY, JSON.stringify(farmers));
            
            console.log('[RecentFarmers] Added farmer:', entry.firstName, entry.lastName);
            return true;
        } catch (e) {
            console.warn('[RecentFarmers] Error adding farmer:', e);
            return false;
        }
    };

    /**
     * Remove a farmer from the recent list
     * @param {string} farmerId - Farmer ID to remove
     * @returns {boolean} Success status
     */
    RecentFarmers.prototype.remove = function(farmerId) {
        if (!this.storageAvailable) {
            return false;
        }

        try {
            var farmers = this.getAll();
            var originalLength = farmers.length;

            farmers = farmers.filter(function(f) {
                return f.id !== farmerId;
            });

            if (farmers.length === originalLength) {
                // Farmer not found
                return false;
            }

            localStorage.setItem(STORAGE_KEY, JSON.stringify(farmers));
            
            console.log('[RecentFarmers] Removed farmer:', farmerId);
            return true;
        } catch (e) {
            console.warn('[RecentFarmers] Error removing farmer:', e);
            return false;
        }
    };

    /**
     * Clear all recent farmers
     * @returns {boolean} Success status
     */
    RecentFarmers.prototype.clear = function() {
        if (!this.storageAvailable) {
            return false;
        }

        try {
            localStorage.removeItem(STORAGE_KEY);
            console.log('[RecentFarmers] Cleared all recent farmers');
            return true;
        } catch (e) {
            console.warn('[RecentFarmers] Error clearing:', e);
            return false;
        }
    };

    /**
     * Get the count of recent farmers
     * @returns {number}
     */
    RecentFarmers.prototype.count = function() {
        return this.getAll().length;
    };

    /**
     * Check if a farmer is in the recent list
     * @param {string} farmerId - Farmer ID to check
     * @returns {boolean}
     */
    RecentFarmers.prototype.contains = function(farmerId) {
        var farmers = this.getAll();
        return farmers.some(function(f) {
            return f.id === farmerId;
        });
    };

    /**
     * Get a specific farmer by ID from the recent list
     * @param {string} farmerId - Farmer ID
     * @returns {Object|null} Farmer object or null if not found
     */
    RecentFarmers.prototype.get = function(farmerId) {
        var farmers = this.getAll();
        for (var i = 0; i < farmers.length; i++) {
            if (farmers[i].id === farmerId) {
                return farmers[i];
            }
        }
        return null;
    };

    /**
     * Check if localStorage is available
     * @returns {boolean}
     */
    RecentFarmers.prototype.isAvailable = function() {
        return this.storageAvailable;
    };

    /**
     * Format timestamp as relative time
     * @param {number} timestamp - Timestamp in milliseconds
     * @returns {string} Human-readable relative time
     */
    RecentFarmers.formatRelativeTime = function(timestamp) {
        if (!timestamp) return '';

        var now = Date.now();
        var diff = now - timestamp;
        var seconds = Math.floor(diff / 1000);
        var minutes = Math.floor(seconds / 60);
        var hours = Math.floor(minutes / 60);
        var days = Math.floor(hours / 24);

        if (days > 0) {
            return days === 1 ? 'yesterday' : days + ' days ago';
        }
        if (hours > 0) {
            return hours === 1 ? '1 hour ago' : hours + ' hours ago';
        }
        if (minutes > 0) {
            return minutes === 1 ? '1 minute ago' : minutes + ' minutes ago';
        }
        return 'just now';
    };

    // Export to global scope
    global.RecentFarmers = RecentFarmers;

    console.log('[RecentFarmers] Loaded successfully');

})(typeof window !== 'undefined' ? window : this);

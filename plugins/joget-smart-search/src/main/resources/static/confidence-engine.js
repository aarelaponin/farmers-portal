/**
 * Confidence Engine - Client-side confidence calculation
 * Phase 6: Smart Farmer Search - Offline Support
 * 
 * Uses cached statistics to estimate search effectiveness and provide
 * real-time confidence feedback as users enter search criteria.
 * Supports localStorage caching for offline PWA usage.
 * 
 * @version 8.1-SNAPSHOT-phase6
 */

(function(global) {
    'use strict';

    var VERSION = '8.1-SNAPSHOT-phase9';
    var CACHE_KEY = 'fss_statistics_cache';
    var DEFAULT_TTL_HOURS = 24;

    console.log('[ConfidenceEngine] Loading v' + VERSION);

    /**
     * ConfidenceEngine - Calculates search confidence based on criteria and statistics
     * @param {Object} config - Optional configuration
     * @param {number} config.nationalIdMinLength - Minimum length for national ID (default: 4)
     * @param {number} config.phoneMinLength - Minimum length for phone (default: 8)
     * @param {number} config.nameMinLength - Minimum length for name search (default: 2)
     * @param {number} config.statisticsTimeout - Timeout for statistics API call in ms (default: 10000)
     */
    function ConfidenceEngine(config) {
        this.statistics = null;
        this.statisticsLoadedAt = null;
        this.usingCachedData = false;
        this.cacheTimestamp = null;

        // Configurable parameters
        config = config || {};
        this.nationalIdMinLength = config.nationalIdMinLength || 4;
        this.phoneMinLength = config.phoneMinLength || 8;
        this.nameMinLength = config.nameMinLength || 2;
        this.statisticsTimeout = config.statisticsTimeout || 10000;
    }

    /**
     * Update configuration
     * @param {Object} config - Configuration object
     */
    ConfidenceEngine.prototype.setConfig = function(config) {
        if (config.nationalIdMinLength !== undefined) {
            this.nationalIdMinLength = config.nationalIdMinLength;
        }
        if (config.phoneMinLength !== undefined) {
            this.phoneMinLength = config.phoneMinLength;
        }
        if (config.nameMinLength !== undefined) {
            this.nameMinLength = config.nameMinLength;
        }
        if (config.statisticsTimeout !== undefined) {
            this.statisticsTimeout = config.statisticsTimeout;
        }
    };

    // ==========================================================================
    // STATISTICS CACHING (Phase 6)
    // ==========================================================================

    /**
     * Cache statistics to localStorage
     * @param {Object} stats - Statistics object from API
     * @param {number} ttlHours - Time to live in hours (default: 24)
     * @returns {boolean} Success status
     */
    ConfidenceEngine.prototype.cacheStatistics = function(stats, ttlHours) {
        try {
            var cacheData = {
                data: stats,
                timestamp: Date.now(),
                ttl_hours: ttlHours || DEFAULT_TTL_HOURS
            };
            localStorage.setItem(CACHE_KEY, JSON.stringify(cacheData));
            console.log('[ConfidenceEngine] Statistics cached successfully');
            return true;
        } catch (e) {
            console.warn('[ConfidenceEngine] Failed to cache statistics:', e);
            return false;
        }
    };

    /**
     * Load cached statistics from localStorage
     * @param {boolean} allowStale - If true, returns stale cache if available
     * @returns {Object|null} Statistics object or null if expired/missing
     */
    ConfidenceEngine.prototype.loadCachedStatistics = function(allowStale) {
        try {
            var cached = localStorage.getItem(CACHE_KEY);
            if (!cached) {
                console.log('[ConfidenceEngine] No cached statistics found');
                return null;
            }

            var cacheData = JSON.parse(cached);
            var age = Date.now() - cacheData.timestamp;
            var ttlMs = (cacheData.ttl_hours || DEFAULT_TTL_HOURS) * 60 * 60 * 1000;

            if (age > ttlMs && !allowStale) {
                console.log('[ConfidenceEngine] Cached statistics expired (' + 
                    Math.round(age / 3600000) + ' hours old)');
                return null;
            }

            console.log('[ConfidenceEngine] Loaded cached statistics (' +
                Math.round(age / 3600000) + ' hours old)');
            
            return {
                data: cacheData.data,
                timestamp: cacheData.timestamp,
                age: age,
                isStale: age > ttlMs
            };
        } catch (e) {
            console.warn('[ConfidenceEngine] Failed to load cached statistics:', e);
            return null;
        }
    };

    /**
     * Check if cached statistics exist (regardless of staleness)
     * @returns {boolean}
     */
    ConfidenceEngine.prototype.hasCachedStatistics = function() {
        try {
            return localStorage.getItem(CACHE_KEY) !== null;
        } catch (e) {
            return false;
        }
    };

    /**
     * Check if cached statistics are stale (expired but still usable)
     * @returns {boolean}
     */
    ConfidenceEngine.prototype.isCacheStale = function() {
        try {
            var cached = localStorage.getItem(CACHE_KEY);
            if (!cached) return true;

            var cacheData = JSON.parse(cached);
            var age = Date.now() - cacheData.timestamp;
            var ttlMs = (cacheData.ttl_hours || DEFAULT_TTL_HOURS) * 60 * 60 * 1000;

            return age > ttlMs;
        } catch (e) {
            return true;
        }
    };

    /**
     * Get cache age in human-readable format
     * @returns {string} E.g., "2 hours ago", "1 day ago"
     */
    ConfidenceEngine.prototype.getCacheAge = function() {
        if (!this.cacheTimestamp) return '';

        var age = Date.now() - this.cacheTimestamp;
        var hours = Math.floor(age / 3600000);
        var days = Math.floor(hours / 24);

        if (days > 0) {
            return days === 1 ? '1 day ago' : days + ' days ago';
        }
        if (hours > 0) {
            return hours === 1 ? '1 hour ago' : hours + ' hours ago';
        }
        var minutes = Math.floor(age / 60000);
        if (minutes > 0) {
            return minutes === 1 ? '1 minute ago' : minutes + ' minutes ago';
        }
        return 'just now';
    };

    /**
     * Clear cached statistics
     * @returns {boolean} Success status
     */
    ConfidenceEngine.prototype.clearCache = function() {
        try {
            localStorage.removeItem(CACHE_KEY);
            console.log('[ConfidenceEngine] Cache cleared');
            return true;
        } catch (e) {
            return false;
        }
    };

    /**
     * Check if currently using cached data (as opposed to fresh API data)
     * @returns {boolean}
     */
    ConfidenceEngine.prototype.isUsingCachedData = function() {
        return this.usingCachedData;
    };

    // ==========================================================================
    // STATISTICS LOADING
    // ==========================================================================

    /**
     * Set statistics data directly
     * @param {Object} stats - Statistics object from API
     * @param {boolean} fromCache - Whether this data came from cache
     * @param {number} cacheTimestamp - Timestamp when data was cached (if from cache)
     */
    ConfidenceEngine.prototype.setStatistics = function(stats, fromCache, cacheTimestamp) {
        this.statistics = stats;
        this.statisticsLoadedAt = new Date();
        this.usingCachedData = !!fromCache;
        this.cacheTimestamp = cacheTimestamp || null;
        
        console.log('[ConfidenceEngine] Statistics set:', {
            totalFarmers: stats ? stats.total_farmers : 0,
            districts: stats && stats.district_counts ? Object.keys(stats.district_counts).length : 0,
            surnameCount: stats && stats.surname_frequency ? Object.keys(stats.surname_frequency).length : 0,
            fromCache: fromCache || false
        });
    };

    /**
     * Load statistics - tries API first, falls back to cache
     * @param {string} apiEndpoint - Base API endpoint
     * @param {string} apiId - API authentication ID
     * @param {string} apiKey - API authentication key
     * @param {boolean} isOnline - Current online status
     * @param {Function} callback - Callback function(error, stats, fromCache)
     */
    ConfidenceEngine.prototype.loadStatistics = function(apiEndpoint, apiId, apiKey, isOnline, callback) {
        var self = this;
        
        // Handle the case where isOnline is actually the callback (backward compatibility)
        if (typeof isOnline === 'function') {
            callback = isOnline;
            isOnline = navigator.onLine;
        }

        // If offline, use cache only
        if (!isOnline) {
            console.log('[ConfidenceEngine] Offline - loading from cache');
            var cached = this.loadCachedStatistics(true); // Allow stale
            if (cached) {
                self.setStatistics(cached.data, true, cached.timestamp);
                callback(null, cached.data, true);
            } else {
                callback('No cached statistics available', null, false);
            }
            return;
        }

        // Check if cache is valid (not stale) - use it without API call
        var cachedData = this.loadCachedStatistics(false); // Don't allow stale
        if (cachedData && !cachedData.isStale) {
            console.log('[ConfidenceEngine] Using valid cached statistics');
            self.setStatistics(cachedData.data, true, cachedData.timestamp);
            callback(null, cachedData.data, true);
            return;
        }

        // Try to load from API
        var url = apiEndpoint + '/statistics';
        console.log('[ConfidenceEngine] Loading statistics from API:', url);

        var xhr = new XMLHttpRequest();
        xhr.open('GET', url, true);
        xhr.setRequestHeader('Content-Type', 'application/json');

        if (apiId) {
            xhr.setRequestHeader('api_id', apiId);
        }
        if (apiKey) {
            xhr.setRequestHeader('api_key', apiKey);
        }

        xhr.onload = function() {
            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    var response = JSON.parse(xhr.responseText);
                    if (response.success && response.statistics) {
                        // Cache the fresh statistics
                        self.cacheStatistics(response.statistics);
                        self.setStatistics(response.statistics, false, null);
                        callback(null, response.statistics, false);
                    } else {
                        // API returned error, try cache
                        self._fallbackToCache(callback, response.error || 'API returned no statistics');
                    }
                } catch (e) {
                    console.error('[ConfidenceEngine] JSON parse error:', e);
                    self._fallbackToCache(callback, 'Invalid response format');
                }
            } else {
                self._fallbackToCache(callback, 'Request failed: ' + xhr.status);
            }
        };

        xhr.onerror = function() {
            self._fallbackToCache(callback, 'Network error');
        };

        xhr.timeout = this.statisticsTimeout;
        xhr.ontimeout = function() {
            self._fallbackToCache(callback, 'Request timeout');
        };

        xhr.send();
    };

    /**
     * Fallback to cached statistics when API fails
     * @private
     */
    ConfidenceEngine.prototype._fallbackToCache = function(callback, errorReason) {
        console.warn('[ConfidenceEngine] API failed (' + errorReason + '), trying cache');
        var cached = this.loadCachedStatistics(true); // Allow stale
        if (cached) {
            console.log('[ConfidenceEngine] Using stale cached statistics');
            this.setStatistics(cached.data, true, cached.timestamp);
            callback(null, cached.data, true);
        } else {
            callback(errorReason, null, false);
        }
    };

    /**
     * Check if statistics are loaded
     * @returns {boolean}
     */
    ConfidenceEngine.prototype.hasStatistics = function() {
        return this.statistics !== null;
    };

    // ==========================================================================
    // CONFIDENCE CALCULATION
    // ==========================================================================

    /**
     * Calculate confidence score (0-100) based on current criteria
     * 
     * Confidence represents how likely the search criteria will return
     * a manageable number of results (ideally < 20).
     * 
     * @param {Object} criteria - Search criteria object
     * @param {string} criteria.nationalId - Full national ID
     * @param {string} criteria.phone - Full phone number
     * @param {string} criteria.name - Name search text
     * @param {string} criteria.districtCode - District code
     * @param {string} criteria.village - Village name
     * @param {string} criteria.partialId - Partial national ID (4+ digits)
     * @param {string} criteria.partialPhone - Partial phone (4+ digits)
     * @param {string} criteria.communityCouncil - Community council
     * @param {string} criteria.cooperative - Cooperative name
     * @returns {number} Confidence score 0-100
     */
    ConfidenceEngine.prototype.calculate = function(criteria) {
        var stats = this.statistics;
        
        // Default confidence if no stats
        if (!stats) {
            console.log('[ConfidenceEngine] No statistics available, returning default 50');
            return 50;
        }

        var factors = stats.effectiveness_factors || {};
        var totalFarmers = stats.total_farmers || 1;
        var expectedResults = totalFarmers;

        // Exact match fields give 100% confidence
        // Uses configurable minimum length
        if (criteria.nationalId && criteria.nationalId.length >= this.nationalIdMinLength) {
            return 100;
        }
        
        if (criteria.phone) {
            var phoneDigits = criteria.phone.replace(/[^\d]/g, '');
            if (phoneDigits.length >= this.phoneMinLength) {
                return 100;
            }
        }

        // Apply district factor
        if (criteria.districtCode && stats.district_counts) {
            var districtCount = stats.district_counts[criteria.districtCode];
            if (districtCount) {
                expectedResults = districtCount;
            } else {
                // Unknown district, estimate 10% of total
                expectedResults = Math.floor(totalFarmers / 10);
            }
        }

        // Apply village factor
        if (criteria.village && criteria.village.trim()) {
            var villageFactor = factors.village || 0.85;
            // Village typically reduces to avg village size
            var villageAvgSize = stats.village_avg_size || 150;
            expectedResults = Math.min(expectedResults, villageAvgSize * 2);
        }

        // Apply name frequency
        if (criteria.name && criteria.name.trim()) {
            var nameParts = criteria.name.toLowerCase().trim().split(/\s+/);
            var nameMultiplier = 1.0;

            for (var i = 0; i < nameParts.length; i++) {
                var part = nameParts[i];
                if (part.length < 2) continue;

                var freq = null;
                
                // Check surname frequency first
                if (stats.surname_frequency && stats.surname_frequency[part]) {
                    freq = stats.surname_frequency[part];
                }
                // Then check firstname frequency
                else if (stats.firstname_frequency && stats.firstname_frequency[part]) {
                    freq = stats.firstname_frequency[part];
                }
                // Use default frequency
                else {
                    freq = (stats.surname_frequency && stats.surname_frequency._default) || 0.0002;
                }

                // Name frequency affects expected results
                // Common names = less reduction, rare names = more reduction
                nameMultiplier *= (freq * 20); // Scale factor to make it meaningful
            }

            expectedResults *= Math.min(1.0, nameMultiplier);
        }

        // Apply partial ID factor (Issue #6: uses config nationalIdMinLength)
        if (criteria.partialId && criteria.partialId.length >= this.nationalIdMinLength) {
            var idFactor = factors.partial_id_4 || 0.92;
            expectedResults *= (1 - idFactor);
        }

        // Apply partial phone factor (Issue #6: uses config phoneMinLength)
        if (criteria.partialPhone) {
            var phonePartDigits = criteria.partialPhone.replace(/[^\d]/g, '');
            if (phonePartDigits.length >= this.phoneMinLength) {
                var phoneFactor = factors.partial_phone_4 || 0.90;
                expectedResults *= (1 - phoneFactor);
            }
        }

        // Apply community council factor
        if (criteria.communityCouncil && criteria.communityCouncil.trim()) {
            var ccFactor = factors.community_council || 0.55;
            expectedResults *= (1 - ccFactor);
        }

        // Apply cooperative factor
        if (criteria.cooperative && criteria.cooperative.trim()) {
            var coopFactor = factors.cooperative || 0.45;
            expectedResults *= (1 - coopFactor);
        }

        // Convert expected results to confidence
        // Target: < 20 results = 100%, > 1000 results = 0%
        var confidence = 100;
        if (expectedResults > 20) {
            // Linear decrease: 20 results = 100%, 1000 results = 0%
            confidence = Math.max(0, 100 - ((expectedResults - 20) / 10));
        }

        // Ensure within bounds
        confidence = Math.round(Math.min(100, Math.max(0, confidence)));

        console.log('[ConfidenceEngine] Calculated confidence:', {
            criteria: criteria,
            expectedResults: Math.round(expectedResults),
            confidence: confidence
        });

        return confidence;
    };

    // ==========================================================================
    // CRITERIA VALIDATION
    // ==========================================================================

    /**
     * Validate criteria against minimum requirements
     * 
     * @param {Object} criteria - Search criteria object
     * @returns {Object} Validation result
     *   - valid: boolean - Whether criteria meet minimum requirements
     *   - type: string - 'exact_match' | 'acceptable' | 'warning' | 'rejected'
     *   - message: string - User-friendly message
     *   - canSearch: boolean - Whether search should be enabled
     */
    ConfidenceEngine.prototype.validateCriteria = function(criteria) {
        // Exact match fields are always acceptable
        // Uses configurable minimum length
        if (criteria.nationalId && criteria.nationalId.length >= this.nationalIdMinLength) {
            return {
                valid: true,
                type: 'exact_match',
                message: '✓ Exact ID match search',
                canSearch: true
            };
        }

        if (criteria.phone) {
            var phoneDigits = criteria.phone.replace(/[^\d]/g, '');
            if (phoneDigits.length >= this.phoneMinLength) {
                return {
                    valid: true,
                    type: 'exact_match',
                    message: '✓ Exact phone match search',
                    canSearch: true
                };
            }
        }

        // Check what criteria we have (Issue #6, #12: uses config min lengths)
        var hasName = !!(criteria.name && criteria.name.trim() && criteria.name.trim().length >= this.nameMinLength);
        var hasDistrict = !!(criteria.districtCode && criteria.districtCode.trim());
        var hasVillage = !!(criteria.village && criteria.village.trim());
        var hasPartialId = !!(criteria.partialId && criteria.partialId.length >= this.nationalIdMinLength);
        var hasPartialPhone = !!(criteria.partialPhone && criteria.partialPhone.replace(/[^\d]/g, '').length >= this.phoneMinLength);
        var hasCommunityCouncil = !!(criteria.communityCouncil && criteria.communityCouncil.trim());
        var hasCooperative = !!(criteria.cooperative && criteria.cooperative.trim());

        // No criteria at all
        if (!hasName && !hasDistrict && !hasVillage && !hasPartialId && !hasPartialPhone && !hasCommunityCouncil && !hasCooperative) {
            return {
                valid: false,
                type: 'rejected',
                message: 'Enter search criteria',
                canSearch: false
            };
        }

        // Name alone is not acceptable
        if (hasName && !hasDistrict && !hasVillage && !hasPartialId && !hasPartialPhone && !hasCommunityCouncil && !hasCooperative) {
            return {
                valid: false,
                type: 'rejected',
                message: '⚠ Please add district or village',
                canSearch: false
            };
        }

        // District alone is not acceptable
        if (hasDistrict && !hasName && !hasPartialId && !hasPartialPhone) {
            return {
                valid: false,
                type: 'rejected',
                message: '⚠ Please add name or ID',
                canSearch: false
            };
        }

        // Village alone is not acceptable
        if (hasVillage && !hasName && !hasPartialId && !hasPartialPhone) {
            return {
                valid: false,
                type: 'rejected',
                message: '⚠ Please add name or ID',
                canSearch: false
            };
        }

        // Name + village is good
        if (hasName && hasVillage) {
            return {
                valid: true,
                type: 'acceptable',
                message: '✓ Ready to search',
                canSearch: true
            };
        }

        // Name + partial ID is good
        if (hasName && hasPartialId) {
            return {
                valid: true,
                type: 'acceptable',
                message: '✓ Ready to search',
                canSearch: true
            };
        }

        // Name + partial phone is good
        if (hasName && hasPartialPhone) {
            return {
                valid: true,
                type: 'acceptable',
                message: '✓ Ready to search',
                canSearch: true
            };
        }

        // Name + community council is good
        if (hasName && hasCommunityCouncil) {
            return {
                valid: true,
                type: 'acceptable',
                message: '✓ Ready to search',
                canSearch: true
            };
        }

        // Name + cooperative + district is good
        if (hasName && hasCooperative && hasDistrict) {
            return {
                valid: true,
                type: 'acceptable',
                message: '✓ Ready to search',
                canSearch: true
            };
        }

        // Name + district is allowed but with warning (too broad)
        if (hasName && hasDistrict && !hasVillage) {
            return {
                valid: true,
                type: 'warning',
                message: '⚠ Results may be broad. Consider adding village.',
                canSearch: true
            };
        }

        // Partial ID alone (4+ digits) is marginally acceptable
        if (hasPartialId && !hasName) {
            return {
                valid: true,
                type: 'warning',
                message: '⚠ Consider adding name for better results',
                canSearch: true
            };
        }

        // Default: allow search with warning
        return {
            valid: true,
            type: 'warning',
            message: '⚠ Add more criteria for better results',
            canSearch: true
        };
    };

    // ==========================================================================
    // UTILITY METHODS
    // ==========================================================================

    /**
     * Get confidence level category
     * @param {number} confidence - Confidence score (0-100)
     * @returns {string} Level: 'high', 'medium', or 'low'
     */
    ConfidenceEngine.prototype.getConfidenceLevel = function(confidence) {
        if (confidence >= 70) return 'high';
        if (confidence >= 40) return 'medium';
        return 'low';
    };

    /**
     * Get CSS class for confidence level
     * @param {number} confidence - Confidence score (0-100)
     * @returns {string} CSS class name
     */
    ConfidenceEngine.prototype.getConfidenceClass = function(confidence) {
        var level = this.getConfidenceLevel(confidence);
        return 'fss-confidence-' + level;
    };

    /**
     * Get display text for confidence level
     * @param {number} confidence - Confidence score (0-100)
     * @returns {string} Display text
     */
    ConfidenceEngine.prototype.getConfidenceText = function(confidence) {
        if (confidence >= 70) return 'Ready to search';
        if (confidence >= 40) return 'Add more criteria for better results';
        return 'Please add criteria';
    };

    // Export to global scope
    global.ConfidenceEngine = ConfidenceEngine;

    console.log('[ConfidenceEngine] Loaded successfully');

})(typeof window !== 'undefined' ? window : this);

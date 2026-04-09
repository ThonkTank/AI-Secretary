/**
 * Mock capability documentation for the future Task Feature slice.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li><b>Scoring Capability</b>: computes effective task scores including parent-score propagation.</li>
 *   <li><b>Dynamic Slot Assignment Capability</b>: assigns MORGEN/VORMITTAG/MITTAG/NACHMITTAG/ABEND based on completion tracking.</li>
 *   <li><b>Planning Capability</b>: fills each slot by descending score while respecting slot time capacity.</li>
 *   <li><b>Nesting-Aware Co-Planning</b>: selected children bring their parent into the same planning decision if needed.</li>
 * </ul>
 *
 * <h2>Architectural Principle</h2>
 * Score computation and time assignment are separated APIs; planning consumes both outputs in a flat evaluation flow.
 */
package com.autosecretary.features.task.application.capability;

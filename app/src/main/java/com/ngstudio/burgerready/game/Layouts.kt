package com.ngstudio.burgerready.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A placed object in world coordinates (metres). yaw in radians; 0 = front faces +Z (towards camera/street). */
data class Placement(val x: Float, val z: Float, val yaw: Float = 0f)

data class TableSlot(val p: Placement, val seats: Int)

data class Decor(val model: String, val x: Float, val z: Float, val yaw: Float = 0f, val scale: Float = 1f)

/**
 * Restaurant layout template. Interior spans x ∈ [0, w], z ∈ [0, d]. The front wall (z = d) has the door;
 * the street runs along +Z beyond the front. Kitchen stations line the back wall (z ≈ 1), the service
 * counter separates kitchen from dining, the register sits at the counter's +X end.
 */
class Layout(
    val id: Int,
    val name: String,
    val w: Int,
    val d: Int,
    val stationSlots: List<Placement>,
    val counter: Placement,
    val tables: List<TableSlot>,
    val trash: Placement,
    val drive: Placement?,          // drive-thru window (on the right wall), null if none
    val doorX: Float,
    val decorInside: List<Decor>,
    val decorOutside: List<Decor>,
    val windowsBack: Set<Int>,
    val windowsSide: Set<Int>,
) {
    val counterZ get() = counter.z
    /** Register end of the counter. */
    val registerX get() = counter.x + 0.85f
    val cashierSpot get() = Placement(registerX, counter.z - 0.85f)
    val queueSpot get() = Placement(registerX, counter.z + 0.95f)
    val dropSpot get() = Placement(counter.x - 0.35f, counter.z - 0.8f)
    val driveCashier: Placement? get() = drive?.let { Placement(it.x - 0.9f, it.z) }
    val driveDrop: Placement? get() = drive?.let { Placement(it.x - 0.9f, it.z + 0.9f) }
    val carStop: Placement? get() = drive?.let { Placement(w + 1.9f, it.z) }
    /** Interior-side spot next to the trash bin (never outside the wall). */
    val trashApproach get() = Placement(trash.x + (if (trash.x < w / 2f) 0.8f else -0.8f), trash.z)
    val sidewalkZ get() = d + 1.0f
    val roadZ get() = d + 3.6f

    /** Queue slot positions (front of the line first). */
    val queueSlots: List<Placement> by lazy {
        val out = ArrayList<Placement>()
        var z = queueSpot.z
        val x = queueSpot.x
        while (z < d - 0.9f) { out.add(Placement(x, z)); z += 0.78f }
        // bend towards the door
        var cx = x
        val zEnd = d - 0.9f
        val dir = if (doorX < x) -1f else 1f
        while (abs(cx - doorX) > 0.5f && out.size < 14) { cx += dir * 0.78f; out.add(Placement(cx, zEnd)) }
        var zz = d + 0.3f
        while (out.size < 16) { out.add(Placement(doorX, zz)); zz += 0.78f }
        out
    }

    fun mirrored(): Layout {
        fun m(p: Placement) = Placement(w - p.x, p.z, -p.yaw)
        fun md(p: Decor) = p.copy(x = w - p.x, yaw = -p.yaw)
        // The register must stay on the +X end of the counter, so the counter keeps its yaw.
        return Layout(id, name, w, d, stationSlots.map { m(it) }.reversed(), Placement(w - counter.x - 1.7f, counter.z, 0f),
            tables.map { TableSlot(m(it.p), it.seats) }, if (drive != null) trash else m(trash), drive, w - doorX,
            decorInside.map { md(it) }, decorOutside.map { md(it) },
            windowsBack.map { w - 1 - it }.toSet(), windowsSide)
    }
}

object Layouts {
    /** (id, name, w, d, stations, tables, drive, minLevel) */
    private data class Spec(val id: Int, val name: String, val w: Int, val d: Int, val stations: Int, val tables: Int, val drive: Boolean, val minLevel: Int)

    private val specs = listOf(
        Spec(1, "Bun Stand", 8, 9, 3, 2, false, 1),
        Spec(2, "Corner Grill", 9, 10, 4, 4, false, 3),
        Spec(3, "Classic Diner", 10, 11, 5, 5, true, 9),
        Spec(4, "Patio Bistro", 11, 11, 5, 6, true, 30),
        Spec(5, "Twin Kitchen", 12, 12, 6, 7, true, 60),
        Spec(6, "Food Court", 13, 12, 7, 8, true, 120),
        Spec(7, "Mega Diner", 14, 13, 7, 9, true, 250),
        Spec(8, "Burger Palace", 15, 14, 8, 10, true, 500),
    )

    private val cache = HashMap<Int, Layout>()

    fun count() = specs.size
    fun minLevel(id: Int) = specs.first { it.id == id }.minLevel
    fun unlockedFor(level: Int): List<Int> = specs.filter { it.minLevel <= level }.map { it.id }
    fun hasDrive(id: Int) = specs.first { it.id == id }.drive

    fun get(id: Int, mirror: Boolean = false): Layout {
        val base = cache.getOrPut(id) { build(specs.first { it.id == id }) }
        return if (mirror) base.mirrored() else base
    }

    private fun build(s: Spec): Layout {
        val w = s.w.toFloat(); val d = s.d.toFloat()
        // Back-wall stations, spaced 1.75 m, centred.
        val n = s.stations
        val spacing = min(1.75f, (w - 1.6f) / n)
        val startX = w / 2f - spacing * (n - 1) / 2f
        val stations = (0 until n).map { Placement(startX + it * spacing, 0.95f) }
        val counterZ = 3.9f
        val counter = Placement(w / 2f - 0.35f, counterZ)
        val regX = counter.x + 0.85f
        val doorX = (regX + 0.3f).coerceIn(2f, w - 2f)
        // Dining tables: left of the queue column and right of it.
        val tables = ArrayList<TableSlot>()
        val zStart = counterZ + 2.0f
        val zEnd = d - 1.3f
        val leftMax = regX - 1.6f
        val rightMin = regX + 1.5f
        var z = zStart
        var row = 0
        while (z <= zEnd && tables.size < s.tables) {
            var x = 1.3f
            while (x <= leftMax - 0.2f && tables.size < s.tables) {
                val four = (row + tables.size) % 3 == 2 && x + 0.5f <= leftMax
                tables.add(TableSlot(Placement(x + if (four) 0.3f else 0f, z), if (four) 4 else 2))
                x += if (four) 2.7f else 2.3f
            }
            x = w - 1.3f
            while (x >= rightMin + 0.2f && tables.size < s.tables) {
                tables.add(TableSlot(Placement(x, z), 2))
                x -= 2.3f
            }
            z += 2.3f; row++
        }
        val trash = Placement(0.75f, counterZ - 0.4f)
        val drive = if (s.drive) Placement(w - 0.35f, 2.3f, (Math.PI / 2).toFloat()) else null
        val decorIn = ArrayList<Decor>()
        decorIn.add(Decor("plant_pot", 0.55f, d - 0.55f))
        decorIn.add(Decor("plant_pot", w - 0.55f, d - 0.55f))
        decorIn.add(Decor("lamp_floor", 0.5f, counterZ + 1.2f))
        if (!s.drive) decorIn.add(Decor("menu_board", w - 0.45f, 1.6f, (-Math.PI / 2).toFloat()))
        if (w >= 11) decorIn.add(Decor("plant_pot", w / 2f - 3.2f, d - 0.55f))
        val decorOut = ArrayList<Decor>()
        decorOut.add(Decor("tree_round", -2.2f, 2.0f)); decorOut.add(Decor("tree_round", -3.0f, d - 3f, 0.7f))
        decorOut.add(Decor("bush", -1.4f, d - 0.6f)); decorOut.add(Decor("bush", w + 1.2f, d + 0.1f))
        decorOut.add(Decor("tree_round", w + (if (s.drive) 4.2f else 2.4f), 1.0f, 1.2f))
        decorOut.add(Decor("lamp_street", -0.8f, d + 1.9f)); decorOut.add(Decor("lamp_street", w + 0.8f, d + 1.9f))
        decorOut.add(Decor("bench", doorX - 4.6f, d + 1.3f))
        decorOut.add(Decor("flower_bed", doorX + 4.4f, d + 0.55f))
        decorOut.add(Decor("tree_round", w / 2f, -2.4f, 0.3f))
        decorOut.add(Decor("bush", 1.5f, -1.2f)); decorOut.add(Decor("bush", w - 2f, -1.3f))
        val winBack = (1 until s.w - 1).filter { it % 3 == 1 }.toSet()
        val winSide = (5 until s.d - 1).filter { it % 3 == 0 }.toSet()
        return Layout(s.id, s.name, s.w, s.d, stations, counter, tables, trash, drive, doorX, decorIn, decorOut, winBack, winSide)
    }
}

/** Walkability grid (0.5 m cells) with A* pathfinding. Bounds extend outside the building for the street. */
class NavGrid(val layout: Layout) {
    val cell = 0.25f
    val minX = -7f
    val minZ = -5f
    val cols: Int = ((layout.w + 14f) / cell).toInt()
    val rows: Int = ((layout.d + 12f) / cell).toInt()
    val blocked = BooleanArray(cols * rows)
    /** Obstacles dilated by one cell: used for planning so characters (radius ~0.24 m) always fit. */
    private val inflated = BooleanArray(cols * rows)
    private var inflatedDirty = true

    private fun ensureInflated() {
        if (!inflatedDirty) return
        inflatedDirty = false
        for (r in 0 until rows) for (c in 0 until cols) {
            var b = false
            for (dr in -1..1) { for (dc in -1..1) {
                val rr = r + dr; val cc = c + dc
                if (rr < 0 || cc < 0 || rr >= rows || cc >= cols || blocked[rr * cols + cc]) { b = true; break }
            }; if (b) break }
            inflated[r * cols + c] = b
        }
    }

    private fun isInflated(x: Float, z: Float): Boolean {
        if (x < minX || z < minZ || x >= minX + cols * cell || z >= minZ + rows * cell) return true
        return inflated[cz(z) * cols + cx(x)]
    }

    fun cx(x: Float) = ((x - minX) / cell).toInt().coerceIn(0, cols - 1)
    fun cz(z: Float) = ((z - minZ) / cell).toInt().coerceIn(0, rows - 1)
    fun centerX(c: Int) = minX + (c + 0.5f) * cell
    fun centerZ(r: Int) = minZ + (r + 0.5f) * cell
    fun isBlocked(x: Float, z: Float): Boolean {
        if (x < minX || z < minZ || x >= minX + cols * cell || z >= minZ + rows * cell) return true
        return blocked[cz(z) * cols + cx(x)]
    }

    fun blockRect(x0: Float, z0: Float, x1: Float, z1: Float) {
        val c0 = cx(min(x0, x1)); val c1 = cx(max(x0, x1) - 0.001f)
        val r0 = cz(min(z0, z1)); val r1 = cz(max(z0, z1) - 0.001f)
        for (r in r0..r1) for (c in c0..c1) blocked[r * cols + c] = true
        inflatedDirty = true
    }

    fun clearRect(x0: Float, z0: Float, x1: Float, z1: Float) {
        val c0 = cx(min(x0, x1)); val c1 = cx(max(x0, x1) - 0.001f)
        val r0 = cz(min(z0, z1)); val r1 = cz(max(z0, z1) - 0.001f)
        for (r in r0..r1) for (c in c0..c1) blocked[r * cols + c] = false
        inflatedDirty = true
    }

    /** Walls with a door gap; stations, counter, tables etc. are added by the World. */
    fun buildWalls() {
        val w = layout.w.toFloat(); val d = layout.d.toFloat()
        val t = 0.2f
        blockRect(-t, -t, w + t, t)                 // back
        blockRect(-t, -t, t, d + t)                 // left
        blockRect(w - t, -t, w + t, d + t)          // right
        blockRect(-t, d - t, layout.doorX - 1.0f, d + t)
        blockRect(layout.doorX + 1.0f, d - t, w + t, d + t)
        // The building's outside: block the far back so NPCs never wander there.
        blockRect(minX, minZ, minX + cols * cell, -1.2f)
        // Road is not walkable except at the crosswalk (NPCs never need it).
        blockRect(minX, d + 2.4f, minX + cols * cell, minZ + rows * cell)
    }

    // ----------------------------------------------------------------- A*
    private val gScore = FloatArray(cols * rows)
    private val came = IntArray(cols * rows)
    private val closed = BooleanArray(cols * rows)
    private val heapIdx = IntArray(cols * rows * 2)
    private val heapF = FloatArray(cols * rows * 2)

    /** Returns a path as alternating x,z floats (excluding the start), smoothed. Null if unreachable. */
    fun path(sx: Float, sz: Float, tx: Float, tz: Float): FloatArray? {
        ensureInflated()
        var start = cz(sz) * cols + cx(sx)
        var goal = cz(tz) * cols + cx(tx)
        if (inflated[goal]) goal = nearestFree(goal) ?: return null
        if (inflated[start]) start = nearestFree(start) ?: return null
        if (start == goal) return floatArrayOf(tx, tz)
        java.util.Arrays.fill(gScore, Float.MAX_VALUE)
        java.util.Arrays.fill(closed, false)
        var heapSize = 0
        fun push(i: Int, f: Float) {
            var k = heapSize++
            heapIdx[k] = i; heapF[k] = f
            while (k > 0) {
                val p = (k - 1) / 2
                if (heapF[p] <= heapF[k]) break
                val ti = heapIdx[p]; heapIdx[p] = heapIdx[k]; heapIdx[k] = ti
                val tf = heapF[p]; heapF[p] = heapF[k]; heapF[k] = tf
                k = p
            }
        }
        fun pop(): Int {
            val top = heapIdx[0]
            heapSize--
            heapIdx[0] = heapIdx[heapSize]; heapF[0] = heapF[heapSize]
            var k = 0
            while (true) {
                val l = k * 2 + 1; val r = l + 1
                var s = k
                if (l < heapSize && heapF[l] < heapF[s]) s = l
                if (r < heapSize && heapF[r] < heapF[s]) s = r
                if (s == k) break
                val ti = heapIdx[s]; heapIdx[s] = heapIdx[k]; heapIdx[k] = ti
                val tf = heapF[s]; heapF[s] = heapF[k]; heapF[k] = tf
                k = s
            }
            return top
        }
        val gc = goal % cols; val gr = goal / cols
        gScore[start] = 0f
        push(start, 0f)
        var found = false
        var iterations = 0
        while (heapSize > 0 && iterations < 30000) {
            iterations++
            val cur = pop()
            if (closed[cur]) continue
            if (cur == goal) { found = true; break }
            closed[cur] = true
            val c = cur % cols; val r = cur / cols
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nc = c + dc; val nr = r + dr
                if (nc < 0 || nr < 0 || nc >= cols || nr >= rows) continue
                val ni = nr * cols + nc
                if (inflated[ni] || closed[ni]) continue
                if (dr != 0 && dc != 0 && (inflated[r * cols + nc] || inflated[nr * cols + c])) continue
                val step = if (dr != 0 && dc != 0) 1.4142f else 1f
                val g = gScore[cur] + step
                if (g < gScore[ni]) {
                    gScore[ni] = g; came[ni] = cur
                    val h = octile(abs(nc - gc), abs(nr - gr))
                    if (heapSize < heapIdx.size) push(ni, g + h)
                }
            }
        }
        if (!found) return null
        val cells = ArrayList<Int>()
        var k = goal
        while (k != start) { cells.add(k); k = came[k] }
        cells.reverse()
        // String-pull smoothing using line-of-sight checks.
        val pts = ArrayList<Float>()
        var anchorX = sx; var anchorZ = sz
        var i = 0
        while (i < cells.size) {
            var j = cells.size - 1
            while (j > i) {
                val jx = centerX(cells[j] % cols); val jz = centerZ(cells[j] / cols)
                if (lineOfSight(anchorX, anchorZ, jx, jz)) break
                j--
            }
            val px = centerX(cells[j] % cols); val pz = centerZ(cells[j] / cols)
            pts.add(px); pts.add(pz)
            anchorX = px; anchorZ = pz
            i = j + 1
        }
        // Only snap the last point to the exact target if the target itself is reachable.
        if (pts.size >= 2 && !isBlocked(tx, tz) && lineOfSight(pts[pts.size - 2], pts[pts.size - 1], tx, tz)) { pts[pts.size - 2] = tx; pts[pts.size - 1] = tz }
        return pts.toFloatArray()
    }

    private fun octile(dx: Int, dy: Int): Float = (dx + dy) + (1.4142f - 2f) * min(dx, dy)

    fun lineOfSight(x0: Float, z0: Float, x1: Float, z1: Float): Boolean {
        ensureInflated()
        val dx = x1 - x0; val dz = z1 - z0
        val len = kotlin.math.sqrt(dx * dx + dz * dz)
        val steps = (len / (cell * 0.5f)).toInt() + 1
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            if (isInflated(x0 + dx * t, z0 + dz * t)) return false
        }
        return true
    }

    fun nearestFree(i: Int): Int? {
        val c0 = i % cols; val r0 = i / cols
        for (rad in 1..14) for (dr in -rad..rad) for (dc in -rad..rad) {
            val c = c0 + dc; val r = r0 + dr
            if (c < 0 || r < 0 || c >= cols || r >= rows) continue
            val k = r * cols + c
            if (!inflated[k]) return k
        }
        return null
    }
}

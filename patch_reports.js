const fs = require('fs');
const file = 'app/src/main/java/com/sukashawarma/pos/presentation/reports/ReportsViewModel.kt';
let content = fs.readFileSync(file, 'utf8');

content = content.replace('const val ORDER_LIST_LIMIT = "500"', 'const val ORDER_LIST_LIMIT = "1000"');

// Delete loadRevenueSummaryFromServer and applyRevenueSummary
content = content.replace(/private suspend fun loadRevenueSummaryFromServer[\s\S]*?isFromLocalCache = false\s*\)\s*\}/, '');

// Update loadFromServer
content = content.replace(/private suspend fun loadFromServer[\s\S]*?loadRevenueSummaryFromServer\(outletId, range\)[\s\S]*?val orders = fetchOrderList\(outletId, range\)[\s\S]*?val shifts = fetchShifts\(outletId, range\)[\s\S]*?isFromLocalCache = false\s*\)/, 
private suspend fun loadFromServer(outletId: String, range: ResolvedDateRange) {
        val orders = fetchOrderList(outletId, range)
        val shifts = fetchShifts(outletId, range)
        
        recomputeFromDtos(orders)

        _analyticsData.value = _analyticsData.value.copy(
            orders = orders,
            shifts = shifts,
            totalCashVariance = shifts.sumOf { it.variance ?: 0.0 },
            isFromLocalCache = false
        ));

// Refactor recomputeFrom
content = content.replace(/private fun recomputeFrom\(entities: List<LocalOrderEntity>\) \{\s*val allDtos = entities\.map \{ it\.toOrderDto\(\) \}\.filter \{ matchesNonStatusFilters\(it\) \}/,
private fun recomputeFrom(entities: List<LocalOrderEntity>) {
        recomputeFromDtos(entities.map { it.toOrderDto() })
    }

    private fun recomputeFromDtos(dtos: List<OrderDto>) {
        val allDtos = dtos.filter { matchesNonStatusFilters(it) });

fs.writeFileSync(file, content);
console.log('Patched');

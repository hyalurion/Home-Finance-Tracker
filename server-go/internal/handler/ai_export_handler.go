package handler

import (
	"fmt"
	"net/http"
	"sort"
	"strconv"
	"strings"

	"homemoney/internal/models"
	"homemoney/internal/repository"

	"github.com/gin-gonic/gin"
	"github.com/xuri/excelize/v2"
	"gorm.io/gorm"
)

// AIDataHandler exports the dataset the AI Q&A feature asks about.
//
// The Web app no longer calls an LLM. It asks the user to download this file,
// copy a generated prompt into an AI chat app and attach the file there, so the
// export has to be deterministic: the filename is derived purely from the
// filters and is echoed back inside the prompt.
type AIDataHandler struct {
	expenseRepo *repository.ExpenseRepository
}

// NewAIDataHandler creates a new AI data export handler
func NewAIDataHandler(expenseRepo *repository.ExpenseRepository) *AIDataHandler {
	return &AIDataHandler{expenseRepo: expenseRepo}
}

// aiDataHeaders maps a locale to its detail-sheet header labels.
var aiDataHeaders = map[string]map[string]string{
	"en-US": {"date": "Date", "type": "Category", "amount": "Amount", "remark": "Notes"},
	"id-ID": {"date": "Tanggal", "type": "Kategori", "amount": "Jumlah", "remark": "Catatan"},
	"ja-JP": {"date": "日付", "type": "カテゴリ", "amount": "金額", "remark": "メモ"},
	"ko-KR": {"date": "날짜", "type": "카테고리", "amount": "금액", "remark": "메모"},
	"ms-MY": {"date": "Tarikh", "type": "Kategori", "amount": "Amaun", "remark": "Nota"},
	"th-TH": {"date": "วันที่", "type": "หมวดหมู่", "amount": "จำนวนเงิน", "remark": "หมายเหตุ"},
	"vi-VN": {"date": "Ngày", "type": "Danh mục", "amount": "Số tiền", "remark": "Ghi chú"},
	"zh-CN": {"date": "日期", "type": "分类", "amount": "金额", "remark": "备注"},
	"zh-HK": {"date": "日期", "type": "分類", "amount": "金額", "remark": "備註"},
	"zh-MO": {"date": "日期", "type": "分類", "amount": "金額", "remark": "備註"},
	"zh-SG": {"date": "日期", "type": "分类", "amount": "金额", "remark": "备注"},
	"zh-TW": {"date": "日期", "type": "分類", "amount": "金額", "remark": "備註"},
}

// resolveAIHeaders returns the header row for lang, falling back to zh-CN.
func resolveAIHeaders(lang string) map[string]string {
	if h, ok := aiDataHeaders[lang]; ok {
		return h
	}
	return aiDataHeaders["zh-CN"]
}

// aiFilters holds the supported query parameters of /api/export/ai-data.
type aiFilters struct {
	year  string
	month string
	types []string
}

// parseAIDataFilters reads and validates the query string.
func parseAIDataFilters(c *gin.Context) aiFilters {
	f := aiFilters{}

	f.year = strings.TrimSpace(c.Query("year"))
	if len(f.year) != 4 {
		f.year = ""
	}

	f.month = strings.TrimSpace(c.Query("month"))
	if len(f.month) != 2 {
		f.month = ""
	}

	for _, t := range strings.Split(c.Query("types"), ",") {
		if t = strings.TrimSpace(t); t != "" {
			f.types = append(f.types, t)
		}
	}
	if len(f.types) > 50 {
		f.types = f.types[:50]
	}

	return f
}

// applyToQuery narrows db down to the filtered, non-deleted expenses.
func (f aiFilters) applyToQuery(db *gorm.DB) *gorm.DB {
	db = db.Where("deletedAt IS NULL")
	if f.year != "" {
		db = db.Where("date LIKE ?", f.year+"-%")
	}
	if f.month != "" {
		db = db.Where("date LIKE ?", "%-"+f.month+"-%")
	}
	if len(f.types) > 0 {
		db = db.Where("type IN ?", f.types)
	}
	return db
}

// describe returns a human readable, prompt friendly description of the scope.
func (f aiFilters) describe() string {
	var scope []string
	switch {
	case f.year != "" && f.month != "":
		scope = append(scope, fmt.Sprintf("年份 %s 年 %s 月", f.year, f.month))
	case f.year != "":
		scope = append(scope, fmt.Sprintf("年份 %s 年", f.year))
	case f.month != "":
		scope = append(scope, fmt.Sprintf("月份 每年 %s 月", f.month))
	default:
		scope = append(scope, "全部时间")
	}

	if len(f.types) > 0 {
		scope = append(scope, "消费类型 "+strings.Join(f.types, "、"))
	} else {
		scope = append(scope, "全部消费类型")
	}
	return strings.Join(scope, "，")
}

// filename is deterministic so the generated prompt can name it exactly.
func (f aiFilters) filename() string {
	base := "expenses"
	switch {
	case f.year != "" && f.month != "":
		base = fmt.Sprintf("expenses_%s-%s", f.year, f.month)
	case f.year != "":
		base = fmt.Sprintf("expenses_%s", f.year)
	case len(f.types) > 0:
		base = fmt.Sprintf("expenses_types_%d", len(f.types))
	}
	return base + ".xlsx"
}

// round2 renders a float with two decimals and no trailing float noise.
func round2(v float64) string {
	return strconv.FormatFloat(v, 'f', 2, 64)
}

// ExtractAIData exports the filtered expenses as an xlsx with two sheets:
// a per-record "明细" sheet and a precomputed "统计" sheet.
func (h *AIDataHandler) ExportAIData(c *gin.Context) {
	lang := c.DefaultQuery("lang", "zh-CN")
	filters := parseAIDataFilters(c)

	var expenses []models.Expense
	query := filters.applyToQuery(h.expenseRepo.DB().Model(&models.Expense{}))
	if err := query.Order("date ASC").Find(&expenses).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Export failed",
		})
		return
	}

	header := resolveAIHeaders(lang)
	detail := "明细"
	summary := "统计"

	f := excelize.NewFile()
	_ = f.SetSheetName("Sheet1", detail)
	_, _ = f.NewSheet(summary)

	_ = f.SetCellValue(detail, "A1", header["date"])
	_ = f.SetCellValue(detail, "B1", header["type"])
	_ = f.SetCellValue(detail, "C1", header["amount"])
	_ = f.SetCellValue(detail, "D1", header["remark"])

	count := len(expenses)
	total := 0.0
	typeCount := map[string]int{}
	monthTotal := map[string]float64{}

	for i, e := range expenses {
		row := i + 2
		total += e.Amount
		typeCount[e.Type]++
		if len(e.Date) >= 7 {
			month := e.Date[:7]
			monthTotal[month] += e.Amount
		}
		_ = f.SetCellValue(detail, fmt.Sprintf("A%d", row), e.Date)
		_ = f.SetCellValue(detail, fmt.Sprintf("B%d", row), e.Type)
		_ = f.SetCellValue(detail, fmt.Sprintf("C%d", row), e.Amount)
		if e.Remark != nil {
			_ = f.SetCellValue(detail, fmt.Sprintf("D%d", row), *e.Remark)
		}
	}

	_ = f.SetColWidth(detail, "A", "A", 12)
	_ = f.SetColWidth(detail, "B", "B", 15)
	_ = f.SetColWidth(detail, "C", "C", 10)
	_ = f.SetColWidth(detail, "D", "D", 30)

	// ---- summary sheet --------------------------------------------------
	_ = f.SetCellValue(summary, "A1", "统计项")
	_ = f.SetCellValue(summary, "B1", "数值")

	sorted := make([]float64, 0, count)
	for _, e := range expenses {
		sorted = append(sorted, e.Amount)
	}
	sort.Float64s(sorted)

	summaryRows := [][2]string{
		{"总记录数", strconv.Itoa(count)},
		{"总金额", round2(total)},
	}
	if count > 0 {
		summaryRows = append(summaryRows,
			[2]string{"平均金额", round2(total / float64(count))},
			[2]string{"中位数", round2((sorted[count/2-1]+sorted[count/2]) / 2)},
			[2]string{"最小金额", round2(sorted[0])},
			[2]string{"最大金额", round2(sorted[count-1])},
		)
	}
	summaryRows = append(summaryRows,
		[2]string{"消费类型种类数", strconv.Itoa(len(typeCount))},
		[2]string{"数据范围", filters.describe()},
	)

	rowNo := 2
	for _, r := range summaryRows {
		_ = f.SetCellValue(summary, fmt.Sprintf("A%d", rowNo), r[0])
		_ = f.SetCellValue(summary, fmt.Sprintf("B%d", rowNo), r[1])
		rowNo++
	}

	rowNo++
	_ = f.SetCellValue(summary, fmt.Sprintf("A%d", rowNo), "消费类型分布")
	rowNo++
	for _, t := range sortedKeys(typeCount) {
		_ = f.SetCellValue(summary, fmt.Sprintf("A%d", rowNo), t)
		_ = f.SetCellValue(summary, fmt.Sprintf("B%d", rowNo), strconv.Itoa(typeCount[t]))
		rowNo++
	}

	rowNo++
	_ = f.SetCellValue(summary, fmt.Sprintf("A%d", rowNo), "月度金额合计")
	rowNo++
	for _, m := range sortedKeysFloat(monthTotal) {
		_ = f.SetCellValue(summary, fmt.Sprintf("A%d", rowNo), m)
		_ = f.SetCellValue(summary, fmt.Sprintf("B%d", rowNo), round2(monthTotal[m]))
		rowNo++
	}

	_ = f.SetColWidth(summary, "A", "A", 24)
	_ = f.SetColWidth(summary, "B", "B", 18)

	filename := filters.filename()
	c.Header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%s", filename))
	c.Header("X-Expense-Filename", filename)

	if err := f.Write(c.Writer); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"message": "Failed to generate Excel file",
		})
	}
}

// sortedKeys returns the map keys ordered for stable, readable output.
func sortedKeys(m map[string]int) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}

// sortedKeysFloat returns the map keys ordered ascending by numeric value.
func sortedKeysFloat(m map[string]float64) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Slice(keys, func(i, j int) bool { return m[keys[i]] < m[keys[j]] })
	return keys
}

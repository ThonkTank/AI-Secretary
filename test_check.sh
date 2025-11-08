#!/bin/bash
# Automated Code Analysis Script for AI Secretary
# Performs static analysis without full build

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${BLUE}=== AI Secretary Code Analysis ===${NC}"
echo ""

# Counters
ERRORS=0
WARNINGS=0
PASSED=0

# 1. Check Project Structure
echo -e "${BLUE}1. Checking Project Structure...${NC}"
REQUIRED_DIRS=(
    "app/src/main/java/com/aisecretary/taskmaster"
    "app/src/main/res/layout"
    "app/src/main/res/values"
    "app/src/main/res/xml"
)

for dir in "${REQUIRED_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        echo -e "  ${GREEN}✓${NC} $dir"
        ((PASSED++))
    else
        echo -e "  ${RED}✗${NC} $dir missing"
        ((ERRORS++))
    fi
done

# 2. Count Source Files
echo ""
echo -e "${BLUE}2. Source Files Analysis...${NC}"
JAVA_COUNT=$(find app/src/main/java -name "*.java" | wc -l)
LAYOUT_COUNT=$(find app/src/main/res/layout -name "*.xml" | wc -l)
echo -e "  ${GREEN}✓${NC} Java files: $JAVA_COUNT"
echo -e "  ${GREEN}✓${NC} Layout files: $LAYOUT_COUNT"
((PASSED+=2))

# 3. Check AndroidManifest Activities
echo ""
echo -e "${BLUE}3. Checking AndroidManifest Activities...${NC}"
MANIFEST="app/src/main/AndroidManifest.xml"

ACTIVITIES=(
    "MainActivity"
    "AddTaskActivity"
    "StatisticsActivity"
    "DailyPlanActivity"
    "BackupActivity"
)

for activity in "${ACTIVITIES[@]}"; do
    if grep -q "android:name=\".$activity\"" "$MANIFEST"; then
        echo -e "  ${GREEN}✓${NC} $activity registered"
        ((PASSED++))
    else
        echo -e "  ${RED}✗${NC} $activity NOT registered"
        ((ERRORS++))
    fi
done

# 4. Check Services and Receivers
echo ""
echo -e "${BLUE}4. Checking Services and Receivers...${NC}"

SERVICES=(
    "RecurringTaskService"
    "NotificationService"
    "WidgetListService"
)

for service in "${SERVICES[@]}"; do
    if grep -q "android:name=\".*$service\"" "$MANIFEST"; then
        echo -e "  ${GREEN}✓${NC} $service registered"
        ((PASSED++))
    else
        echo -e "  ${RED}✗${NC} $service NOT registered"
        ((ERRORS++))
    fi
done

RECEIVERS=(
    "TaskWidgetProvider"
    "TaskWidgetProviderMedium"
    "TaskWidgetProviderSmall"
    "NotificationReceiver"
)

for receiver in "${RECEIVERS[@]}"; do
    if grep -q "android:name=\".*$receiver\"" "$MANIFEST"; then
        echo -e "  ${GREEN}✓${NC} $receiver registered"
        ((PASSED++))
    else
        echo -e "  ${YELLOW}⚠${NC} $receiver NOT registered"
        ((WARNINGS++))
    fi
done

# 5. Check Layout Files
echo ""
echo -e "${BLUE}5. Checking Required Layout Files...${NC}"

LAYOUTS=(
    "activity_main"
    "activity_add_task"
    "activity_statistics"
    "activity_backup"
    "list_item_task"
    "dialog_completion"
    "fragment_task_basis"
    "fragment_task_recurrence"
    "fragment_task_details"
    "widget_large"
    "widget_medium"
    "widget_small"
)

for layout in "${LAYOUTS[@]}"; do
    if [ -f "app/src/main/res/layout/${layout}.xml" ]; then
        echo -e "  ${GREEN}✓${NC} ${layout}.xml"
        ((PASSED++))
    else
        echo -e "  ${RED}✗${NC} ${layout}.xml missing"
        ((ERRORS++))
    fi
done

# 6. Check Key Java Files
echo ""
echo -e "${BLUE}6. Checking Key Java Files...${NC}"

KEY_FILES=(
    "MainActivity.java"
    "TaskEntity.java"
    "TaskDao.java"
    "TaskRepository.java"
    "TaskAdapter.java"
    "StreakManager.java"
    "RecurrenceManager.java"
    "BackupManager.java"
    "CategoryManager.java"
    "NotificationManager.java"
)

for file in "${KEY_FILES[@]}"; do
    if find app/src/main/java -name "$file" | grep -q .; then
        echo -e "  ${GREEN}✓${NC} $file"
        ((PASSED++))
    else
        echo -e "  ${RED}✗${NC} $file missing"
        ((ERRORS++))
    fi
done

# 7. Check for common syntax issues
echo ""
echo -e "${BLUE}7. Basic Syntax Checks...${NC}"

# Check for package declaration in all Java files
JAVA_NO_PACKAGE=$(find app/src/main/java -name "*.java" -exec grep -L "^package" {} \;)
if [ -z "$JAVA_NO_PACKAGE" ]; then
    echo -e "  ${GREEN}✓${NC} All Java files have package declaration"
    ((PASSED++))
else
    echo -e "  ${RED}✗${NC} Files missing package declaration:"
    echo "$JAVA_NO_PACKAGE"
    ((ERRORS++))
fi

# Check for unclosed brackets (basic check)
BRACKET_ERRORS=$(find app/src/main/java -name "*.java" -exec grep -c '{' {} \; | awk '{sum+=$1} END {print sum}')
BRACKET_CLOSES=$(find app/src/main/java -name "*.java" -exec grep -c '}' {} \; | awk '{sum+=$1} END {print sum}')

if [ "$BRACKET_ERRORS" -eq "$BRACKET_CLOSES" ]; then
    echo -e "  ${GREEN}✓${NC} Bracket balance check passed"
    ((PASSED++))
else
    echo -e "  ${YELLOW}⚠${NC} Bracket mismatch: { = $BRACKET_ERRORS, } = $BRACKET_CLOSES"
    ((WARNINGS++))
fi

# 8. Summary
echo ""
echo -e "${BLUE}=== Test Summary ===${NC}"
echo ""
echo -e "  ${GREEN}Passed:${NC}   $PASSED checks"
echo -e "  ${YELLOW}Warnings:${NC} $WARNINGS checks"
echo -e "  ${RED}Errors:${NC}   $ERRORS checks"
echo ""

if [ $ERRORS -eq 0 ] && [ $WARNINGS -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed!${NC}"
    echo -e "${GREEN}Code structure is ready for build.${NC}"
    exit 0
elif [ $ERRORS -eq 0 ]; then
    echo -e "${YELLOW}⚠ Code structure looks good with minor warnings.${NC}"
    echo -e "${BLUE}Warnings should be reviewed but won't block build.${NC}"
    exit 0
else
    echo -e "${RED}✗ Code has structural issues that need attention.${NC}"
    echo -e "${RED}Please fix errors before attempting to build.${NC}"
    exit 1
fi

#!/bin/bash
# Test script to demonstrate the new -f flag functionality

set -e

echo "=== Testing Recipe File Execution (-f flag) ==="
echo ""

# Test 1: Run the analyze_log recipe from file
echo "Test 1: Running analyze_log recipe from file..."
echo "Command: askimo -f samples/recipes/analyze_log.yml samples/recipes/http_access_analyze.csv"
echo ""
# Uncomment to run:
# askimo -f samples/recipes/analyze_log.yml samples/recipes/http_access_analyze.csv

# Test 2: Run summarize_report with stdin
echo "Test 2: Running summarize_report recipe with stdin..."
echo "Command: echo 'Sample report content...' | askimo -f samples/recipes/summarize_report.yml"
echo ""
# Uncomment to run:
# echo "This is a quarterly report. Revenue increased by 15% year over year. Team size grew from 50 to 65 employees. Main challenge is scaling infrastructure. Recommendation: invest in automation." | askimo -f samples/recipes/summarize_report.yml

# Test 3: Run code_review with git diff
echo "Test 3: Running code_review recipe with git diff..."
echo "Command: git diff HEAD~1 | askimo -f samples/recipes/code_review.yml"
echo ""
# Uncomment to run:
# git diff HEAD~1 | askimo -f samples/recipes/code_review.yml

# Test 4: Run recipe with overrides
echo "Test 4: Running recipe with --set overrides..."
echo "Command: askimo -f recipes/custom.yml --set threshold=500 --set format=json data.csv"
echo ""
# Uncomment to run:
# askimo -f recipes/custom.yml --set threshold=500 --set format=json data.csv

echo "=== All test commands prepared ==="
echo "Uncomment lines in this script to run actual tests"


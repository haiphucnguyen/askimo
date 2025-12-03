# Recipe Samples

This directory contains sample recipes and example files to demonstrate the capabilities of Askimo's recipe system.

## How to Run Recipes

There are two ways to run recipes:

**Option 1: Run directly from file (no installation needed):**
```bash
askimo -f samples/recipes/analyze_log.yml < sample_application.log
```

**Option 2: Install first, then run by name:**
```bash
# First install (one time)
# Then run by name
askimo -r analyze_log < sample_application.log
```

## Available Recipes

## analyze_log.yml

A recipe that analyzes log files and provides insights about errors, warnings, patterns, and recommendations.

### Usage

**Run directly from file:**
```bash
# With piped input
cat sample_application.log | askimo -f samples/recipes/analyze_log.yml

# With CSV data
askimo -f samples/recipes/analyze_log.yml http_access_analyze.csv
```

**After installation:**
```bash
cat sample_application.log | askimo -r analyze_log
```

**With kubectl logs:**
```bash
kubectl logs pod-name | askimo -r analyze_log
```

**With Docker logs:**
```bash
docker logs container-name | askimo -r analyze_log
```

**With system logs:**
```bash
tail -n 100 /var/log/application.log | askimo -r analyze_log
```

**Analyze recent journalctl logs:**
```bash
journalctl -u myservice -n 200 | askimo -r analyze_log
```

### What it analyzes

The recipe provides comprehensive analysis including:
- **Summary**: Brief overview of the log analysis
- **Errors & Warnings**: Categorized by severity with line numbers
- **Patterns**: Notable trends or recurring issues
- **Recommendations**: Actionable steps to resolve problems

### Example Output

When analyzing the sample_application.log, the recipe will identify:
- Payment gateway timeout issues
- Redis connection pool exhaustion
- Slow database queries in OrderController
- Database connection pool exhaustion
- Memory and system resource warnings

## sample_application.log

A sample application log file containing various types of log entries:
- Normal application startup
- HTTP request/response logs
- Performance warnings (slow queries)
- Connection timeouts and errors
- Resource exhaustion issues
- System health alerts

This file can be used to test the analyze_log recipe.

## summarize_report.yml

A recipe that summarizes documents or reports into 5 clear bullet points with a key takeaway.

### Usage

**Summarize a document:**
```bash
cat report.txt | askimo -f samples/recipes/summarize_report.yml
```

**Summarize a PDF (after converting to text):**
```bash
pdftotext report.pdf - | askimo -f samples/recipes/summarize_report.yml
```

### What it provides

- 5 specific, actionable bullet points
- Each bullet is self-contained and focused on outcomes
- One sentence key takeaway at the end
- Professional format suitable for sharing with teams

## code_review.yml

A recipe that reviews code changes against a standard checklist covering correctness, readability, performance, security, and test coverage.

### Usage

**Review recent changes:**
```bash
git diff HEAD~1 | askimo -f samples/recipes/code_review.yml
```

**Review staged changes:**
```bash
git diff --cached | askimo -f samples/recipes/code_review.yml
```

**Review a specific file:**
```bash
cat MyClass.java | askimo -f samples/recipes/code_review.yml
```

### What it provides

- Overall assessment summary
- Specific issues with file:line references
- Actionable recommendations
- Test scenarios to consider

## http_access_analyze.csv

Sample HTTP access log data in CSV format containing:
- IP addresses and timestamps
- HTTP methods and paths
- Status codes and response times
- User agents

Can be used with the analyze_log recipe to identify:
- Slow endpoints
- Error patterns
- Traffic spikes
- Suspicious activity

## Try them yourself

```bash
# Analyze application logs
cat samples/recipes/sample_application.log | askimo -f samples/recipes/analyze_log.yml

# Analyze HTTP access logs
askimo -f samples/recipes/analyze_log.yml samples/recipes/http_access_analyze.csv

# Review your recent code changes
git diff HEAD~1 | askimo -f samples/recipes/code_review.yml

# Summarize a document
echo "Your document content here..." | askimo -f samples/recipes/summarize_report.yml
```

## Automation Examples

**Hourly system health check:**
```bash
# Add to crontab
0 * * * * kubectl logs my-app | askimo -f ~/recipes/analyze_log.yml | mail -s "Hourly Health Report" team@company.com
```

**Pre-commit code review:**
```bash
# Add to .git/hooks/pre-commit
git diff --cached | askimo -f recipes/code_review.yml > /tmp/review.txt
if grep -q "Issues Found" /tmp/review.txt; then
    cat /tmp/review.txt
    exit 1
fi
```

## Learn More

For more details on creating and using recipes, see:
- [Using Recipes Guide](/docs/using-recipes.md)
- [Blog Post: Using Recipes](/docs/_posts/2025-12-02-using-recipes-in-askimo.md)


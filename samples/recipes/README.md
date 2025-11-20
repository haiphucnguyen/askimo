# Recipe Samples

This directory contains sample recipes and example files to demonstrate the capabilities of Askimo's recipe system.

## analyze_log.yml

A recipe that analyzes log files and provides insights about errors, warnings, patterns, and recommendations.

### Usage

**Basic usage with piped input:**
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

## Try it yourself

```bash
# From the project root
cat samples/recipes/sample_application.log | askimo -r analyze_log
```


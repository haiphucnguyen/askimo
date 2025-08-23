# Contributing to Askimo

Thanks for considering a contribution! We welcome issues, feature requests, and pull requests.
To keep contributions clear and legally safe for everyone, Askimo uses the Developer Certificate of Origin (DCO).

# 📝 Developer Certificate of Origin (DCO)

The DCO is a lightweight alternative to a Contributor License Agreement (CLA).
By signing off your commits, you certify that:

> The contribution is your original work, or you have the right to submit it under the project’s license, and you 
> agree it can be distributed under the Apache 2.0 License.

The full text is available here: https://developercertificate.org/

## ✅ How to sign off a commit

When you make a commit, add the -s flag:

```bash
git commit -s -m "Add new feature"
```

This appends a Signed-off-by line to your commit message, e.g.:

```bash
Signed-off-by: Your Name <your.email@example.com>
```

## 🔄 Amending a commit to add a sign-off

If you forgot to sign off:
```bash
git commit --amend -s
git push --force-with-lease
```

For multiple commits:
```bash
git rebase --exec 'git commit --amend -s --no-edit' main
git push --force-with-lease
```

## 🤖 Enforcing DCO

To make sure every PR is compliant, we use the **DCO GitHub App**.  
It automatically checks for the `Signed-off-by` line on each commit.  
PRs without it will be flagged until all commits are signed.
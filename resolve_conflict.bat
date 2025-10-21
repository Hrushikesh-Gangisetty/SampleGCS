@echo off
cd /d "C:\Users\akhila.PAVAMANK\StudioProjects\SampleGCS"
echo Checking Git status...
git status
echo.
echo Showing merge conflict details...
git diff --name-only --diff-filter=U
echo.
echo If there are no conflicts showing above, the merge is already resolved.
echo Adding all files and completing the merge...
git add .
echo.
echo Committing the merge...
git commit -m "Merge remote changes with TTS implementation"
echo.
echo Pushing to remote...
git push
echo.
echo Done! Check the output above for any errors.
pause


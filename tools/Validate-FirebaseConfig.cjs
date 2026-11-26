const fs = require('node:fs');
function validate(config, packageName) {
  if (!config.project_info?.project_id) throw Error('Firebase project_id is missing.');
  const client = config.client?.find(c => c.client_info?.android_client_info?.package_name === packageName);
  if (!client) throw Error(`Firebase JSON has no Android client for ${packageName}. Download the matching configuration; do not edit its package name.`);
  if (!client.client_info?.mobilesdk_app_id || !client.api_key?.some(k => k.current_key)) throw Error('Firebase Android app ID or API key is missing.');
  if (!client.oauth_client?.some(c => c.client_type === 3 && c.client_id)) throw Error('Google sign-in web OAuth client is missing. Enable Google Authentication, register the signing SHA-1, and download updated JSON.');
}
module.exports = {validate};
if (require.main === module) {
  try {
    const gradle = fs.readFileSync('app/build.gradle.kts', 'utf8');
    const packageName = gradle.match(/applicationId\s*=\s*"([^"]+)"/)?.[1];
    if (!packageName) throw Error('Cannot determine applicationId.');
    validate(JSON.parse(fs.readFileSync('app/google-services.json', 'utf8')), packageName);
    console.log(`Firebase and Google sign-in configuration matches ${packageName}. Server setup still requires device testing.`);
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}

console.log('id;name;email;address;phone');
for (var i = 0; i < 10000; i++) {
	console.log(`${i};Billy Bob ${i};billy.bob.${i}@foobar.org;Here-${i};${i}-0808080808`);
}
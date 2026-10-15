from flask import Blueprint, render_template, request, redirect, url_for, flash
from flask_login import login_user, logout_user, login_required, current_user
from models import db, User, StatementHistory

auth = Blueprint('auth', __name__)

@auth.route('/login', methods=['GET', 'POST'])
def login():
    if current_user.is_authenticated:
        return redirect(url_for('index'))
        
    if request.method == 'POST':
        email = request.form.get('email')
        password = request.form.get('password')
        
        user = User.query.filter_by(email=email).first()
        if user and user.check_password(password):
            login_user(user)
            return redirect(url_for('index'))
        else:
            flash('Invalid email or password', 'error')
            
    return render_template('login.html')

@auth.route('/register', methods=['GET', 'POST'])
def register():
    if current_user.is_authenticated:
        return redirect(url_for('index'))
        
    if request.method == 'POST':
        name = request.form.get('name')
        email = request.form.get('email')
        password = request.form.get('password')
        confirm_password = request.form.get('confirm_password')
        
        if password != confirm_password:
            flash('Passwords do not match', 'error')
            return redirect(url_for('auth.register'))
            
        existing_user = User.query.filter_by(email=email).first()
        if existing_user:
            flash('Email already exists', 'error')
            return redirect(url_for('auth.register'))
            
        new_user = User(name=name, email=email)
        new_user.set_password(password)
        db.session.add(new_user)
        db.session.commit()
        
        flash('Registration successful. Please log in.', 'success')
        return redirect(url_for('auth.login'))
        
    return render_template('register.html')

@auth.route('/logout')
@login_required
def logout():
    logout_user()
    return redirect(url_for('auth.login'))

@auth.route('/dashboard')
@login_required
def dashboard():
    statements = StatementHistory.query.filter_by(user_id=current_user.id).order_by(StatementHistory.parsed_at.desc()).all()
    
    total_statements = len(statements)
    total_transactions = sum(s.transaction_count for s in statements)
    
    card_counts = {}
    for s in statements:
        card_counts[s.card_name] = card_counts.get(s.card_name, 0) + 1
    
    most_used_card = max(card_counts, key=card_counts.get) if card_counts else "None"
    
    return render_template('dashboard.html', 
                           statements=statements,
                           total_statements=total_statements,
                           total_transactions=total_transactions,
                           most_used_card=most_used_card)
